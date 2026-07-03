package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class PaymentPersistenceIntegrationTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-03T09:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_payment_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentIdempotencyRecordRepository
        idempotencyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesPaymentSchemaMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '11'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void persistsReloadsAndRejectsPayment() {
        UUID actorId = insertIdentityUser();
        UUID sourceAccountId =
            insertCustomerAccount(
                "Payment Source Customer"
            );
        UUID destinationAccountId =
            insertCustomerAccount(
                "Payment Destination Customer"
            );

        Payment payment =
            Payment.pending(
                actorId,
                request(
                    sourceAccountId,
                    destinationAccountId
                ),
                CREATED_AT
            );

        paymentRepository.saveAndFlush(payment);
        entityManager.clear();

        Payment pending =
            paymentRepository
                .findById(payment.id())
                .orElseThrow();

        assertThat(pending.actorIdentityId())
            .isEqualTo(actorId);

        assertThat(pending.request())
            .isEqualTo(
                request(
                    sourceAccountId,
                    destinationAccountId
                )
            );

        assertThat(pending.status())
            .isEqualTo(PaymentStatus.PENDING);

        assertThat(pending.ledgerTransactionId())
            .isNull();

        assertThat(pending.rejectionReason())
            .isNull();

        assertThat(pending.failureReason())
            .isNull();

        assertThat(pending.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(pending.updatedAt())
            .isEqualTo(CREATED_AT);

        assertThat(pending.version())
            .isZero();

        Instant processingAt =
            CREATED_AT.plusSeconds(30L);

        pending.startProcessing(processingAt);
        paymentRepository.saveAndFlush(pending);
        entityManager.clear();

        Payment processing =
            paymentRepository
                .findById(payment.id())
                .orElseThrow();

        assertThat(processing.status())
            .isEqualTo(PaymentStatus.PROCESSING);

        assertThat(processing.updatedAt())
            .isEqualTo(processingAt);

        assertThat(processing.version())
            .isEqualTo(1L);

        Instant rejectedAt =
            CREATED_AT.plusSeconds(60L);

        processing.reject(
            PaymentRejectionReason
                .INSUFFICIENT_FUNDS,
            rejectedAt
        );

        paymentRepository.saveAndFlush(processing);
        entityManager.clear();

        Payment rejected =
            paymentRepository
                .findById(payment.id())
                .orElseThrow();

        assertThat(rejected.status())
            .isEqualTo(PaymentStatus.REJECTED);

        assertThat(rejected.rejectionReason())
            .isEqualTo(
                PaymentRejectionReason
                    .INSUFFICIENT_FUNDS
            );

        assertThat(rejected.ledgerTransactionId())
            .isNull();

        assertThat(rejected.failureReason())
            .isNull();

        assertThat(rejected.updatedAt())
            .isEqualTo(rejectedAt);

        assertThat(rejected.version())
            .isEqualTo(2L);
    }

    @Test
    void persistsAndCompletesIdempotencyReservation() {
        UUID actorId = insertIdentityUser();
        UUID sourceAccountId =
            insertCustomerAccount(
                "Reservation Source Customer"
            );
        UUID destinationAccountId =
            insertCustomerAccount(
                "Reservation Destination Customer"
            );

        PaymentRequestData request =
            request(
                sourceAccountId,
                destinationAccountId
            );

        Payment payment =
            Payment.pending(
                actorId,
                request,
                CREATED_AT
            );

        paymentRepository.saveAndFlush(payment);

        UUID ownerToken = UUID.randomUUID();

        PaymentIdempotencyRecord reservation =
            PaymentIdempotencyRecord.reserve(
                actorId,
                PaymentOperation
                    .CREATE_INTERNAL_PAYMENT,
                IdempotencyKey.of(
                    "reservation-1001"
                ),
                PaymentRequestFingerprint.from(
                    request
                ),
                payment.id(),
                ownerToken,
                CREATED_AT
            );

        idempotencyRepository
            .saveAndFlush(reservation);

        entityManager.clear();

        PaymentIdempotencyRecord processing =
            idempotencyRepository
                .findByActorIdentityIdAndOperationAndIdempotencyKey(
                    actorId,
                    PaymentOperation
                        .CREATE_INTERNAL_PAYMENT,
                    "reservation-1001"
                )
                .orElseThrow();

        assertThat(processing.status())
            .isEqualTo(
                PaymentIdempotencyStatus.PROCESSING
            );

        assertThat(processing.processingOwnerToken())
            .isEqualTo(ownerToken);

        assertThat(
            processing.processingLeaseExpiresAt()
        )
            .isEqualTo(
                CREATED_AT.plus(
                    PaymentIdempotencyRecord
                        .PROCESSING_LEASE
                )
            );

        assertThat(processing.storedResponse())
            .isEmpty();

        assertThat(processing.version())
            .isZero();

        Instant completedAt =
            CREATED_AT.plusSeconds(30L);

        StoredPaymentResponse response =
            new StoredPaymentResponse(
                201,
                StoredPaymentResponse
                    .APPLICATION_JSON,
                """
                {"paymentId":"%s"}
                """
                    .formatted(payment.id())
                    .trim()
            );

        processing.complete(
            ownerToken,
            response,
            completedAt
        );

        idempotencyRepository
            .saveAndFlush(processing);

        entityManager.clear();

        PaymentIdempotencyRecord completed =
            idempotencyRepository
                .findById(reservation.id())
                .orElseThrow();

        assertThat(completed.status())
            .isEqualTo(
                PaymentIdempotencyStatus.COMPLETED
            );

        assertThat(completed.processingOwnerToken())
            .isNull();

        assertThat(
            completed.processingLeaseExpiresAt()
        )
            .isNull();

        assertThat(completed.storedResponse())
            .contains(response);

        assertThat(completed.retentionExpiresAt())
            .isEqualTo(
                completedAt.plus(
                    PaymentIdempotencyRecord
                        .TERMINAL_RETENTION
                )
            );

        assertThat(completed.updatedAt())
            .isEqualTo(completedAt);

        assertThat(completed.version())
            .isEqualTo(1L);
    }

    @Test
    void databaseRejectsDuplicateIdempotencyScope() {
        UUID actorId = insertIdentityUser();
        UUID sourceAccountId =
            insertCustomerAccount(
                "Duplicate Source Customer"
            );
        UUID destinationAccountId =
            insertCustomerAccount(
                "Duplicate Destination Customer"
            );

        PaymentRequestData request =
            request(
                sourceAccountId,
                destinationAccountId
            );

        Payment firstPayment =
            Payment.pending(
                actorId,
                request,
                CREATED_AT
            );

        Payment secondPayment =
            Payment.pending(
                actorId,
                request,
                CREATED_AT
            );

        paymentRepository.saveAndFlush(firstPayment);
        paymentRepository.saveAndFlush(secondPayment);

        PaymentRequestFingerprint fingerprint =
            PaymentRequestFingerprint.from(
                request
            );

        idempotencyRepository.saveAndFlush(
            PaymentIdempotencyRecord.reserve(
                actorId,
                PaymentOperation
                    .CREATE_INTERNAL_PAYMENT,
                IdempotencyKey.of(
                    "duplicate-scope"
                ),
                fingerprint,
                firstPayment.id(),
                UUID.randomUUID(),
                CREATED_AT
            )
        );

        assertThatThrownBy(
            () ->
                idempotencyRepository
                    .saveAndFlush(
                        PaymentIdempotencyRecord
                            .reserve(
                                actorId,
                                PaymentOperation
                                    .CREATE_INTERNAL_PAYMENT,
                                IdempotencyKey.of(
                                    "duplicate-scope"
                                ),
                                fingerprint,
                                secondPayment.id(),
                                UUID.randomUUID(),
                                CREATED_AT
                            )
                    )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsCompletedPaymentWithoutLedger() {
        UUID actorId = insertIdentityUser();
        UUID sourceAccountId =
            insertCustomerAccount(
                "Invalid Completed Source"
            );
        UUID destinationAccountId =
            insertCustomerAccount(
                "Invalid Completed Destination"
            );

        assertThatThrownBy(
            () ->
                insertPayment(
                    actorId,
                    sourceAccountId,
                    destinationAccountId,
                    "COMPLETED",
                    null,
                    null,
                    null
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsMalformedCompletedReservation() {
        UUID actorId = insertIdentityUser();
        UUID sourceAccountId =
            insertCustomerAccount(
                "Malformed Reservation Source"
            );
        UUID destinationAccountId =
            insertCustomerAccount(
                "Malformed Reservation Destination"
            );

        Payment payment =
            Payment.pending(
                actorId,
                request(
                    sourceAccountId,
                    destinationAccountId
                ),
                CREATED_AT
            );

        paymentRepository.saveAndFlush(payment);

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payment_idempotency (
                        id,
                        actor_identity_id,
                        operation,
                        idempotency_key,
                        request_fingerprint,
                        payment_id,
                        status,
                        processing_owner_token,
                        processing_lease_expires_at,
                        response_status,
                        response_media_type,
                        response_body,
                        retention_expires_at,
                        created_at,
                        updated_at,
                        version
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?
                    )
                    """,
                    UUID.randomUUID(),
                    actorId,
                    "CREATE_INTERNAL_PAYMENT",
                    "malformed-completed",
                    PaymentRequestFingerprint
                        .from(payment.request())
                        .value(),
                    payment.id(),
                    "COMPLETED",
                    UUID.randomUUID(),
                    CREATED_AT
                        .plusSeconds(300L)
                        .atOffset(ZoneOffset.UTC),
                    201,
                    "application/json",
                    "{}",
                    CREATED_AT
                        .plusSeconds(86_400L)
                        .atOffset(ZoneOffset.UTC),
                    CREATED_AT.atOffset(
                        ZoneOffset.UTC
                    ),
                    CREATED_AT.atOffset(
                        ZoneOffset.UTC
                    ),
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsOversizedStoredResponse() {
        UUID actorId = insertIdentityUser();
        UUID sourceAccountId =
            insertCustomerAccount(
                "Oversized Response Source"
            );
        UUID destinationAccountId =
            insertCustomerAccount(
                "Oversized Response Destination"
            );

        Payment payment =
            Payment.pending(
                actorId,
                request(
                    sourceAccountId,
                    destinationAccountId
                ),
                CREATED_AT
            );

        paymentRepository.saveAndFlush(payment);

        String oversizedBody =
            "\""
                + "a".repeat(
                    StoredPaymentResponse
                        .MAX_BODY_BYTES
                )
                + "\"";

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payment_idempotency (
                        id,
                        actor_identity_id,
                        operation,
                        idempotency_key,
                        request_fingerprint,
                        payment_id,
                        status,
                        processing_owner_token,
                        processing_lease_expires_at,
                        response_status,
                        response_media_type,
                        response_body,
                        retention_expires_at,
                        created_at,
                        updated_at,
                        version
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?
                    )
                    """,
                    UUID.randomUUID(),
                    actorId,
                    "CREATE_INTERNAL_PAYMENT",
                    "oversized-response",
                    PaymentRequestFingerprint
                        .from(payment.request())
                        .value(),
                    payment.id(),
                    "COMPLETED",
                    null,
                    null,
                    201,
                    "application/json",
                    oversizedBody,
                    CREATED_AT
                        .plusSeconds(86_400L)
                        .atOffset(ZoneOffset.UTC),
                    CREATED_AT.atOffset(
                        ZoneOffset.UTC
                    ),
                    CREATED_AT.atOffset(
                        ZoneOffset.UTC
                    ),
                    0L
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    private PaymentRequestData request(
        UUID sourceAccountId,
        UUID destinationAccountId
    ) {
        return new PaymentRequestData(
            sourceAccountId,
            destinationAccountId,
            GbpAmount.ofMinorUnits(1_250L)
        );
    }

    private UUID insertIdentityUser() {
        UUID identityUserId =
            UUID.randomUUID();

        String email =
            identityUserId
                + "@payment.test";

        jdbcTemplate.update(
            """
            INSERT INTO identity_user (
                id,
                email,
                normalized_email,
                password_hash,
                status,
                failed_login_attempts,
                locked_until,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            identityUserId,
            email,
            email,
            "payment-test-password-hash",
            "ACTIVE",
            0,
            null,
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC),
            0L
        );

        return identityUserId;
    }

    private UUID insertCustomerAccount(
        String customerName
    ) {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO customer_profile (
                id,
                full_name,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            customerId,
            customerName,
            "ACTIVE",
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC),
            0L
        );

        jdbcTemplate.update(
            """
            INSERT INTO customer_account (
                id,
                customer_id,
                currency,
                balance_minor_units,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            customerId,
            "GBP",
            5_000L,
            "ACTIVE",
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC),
            0L
        );

        return accountId;
    }

    private void insertPayment(
        UUID actorId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String status,
        UUID ledgerTransactionId,
        String rejectionReason,
        String failureReason
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO payment (
                id,
                actor_identity_id,
                source_account_id,
                destination_account_id,
                amount_minor_units,
                currency,
                status,
                ledger_transaction_id,
                rejection_reason,
                failure_reason,
                created_at,
                updated_at,
                version
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """,
            UUID.randomUUID(),
            actorId,
            sourceAccountId,
            destinationAccountId,
            1_250L,
            "GBP",
            status,
            ledgerTransactionId,
            rejectionReason,
            failureReason,
            CREATED_AT.atOffset(ZoneOffset.UTC),
            CREATED_AT.atOffset(ZoneOffset.UTC),
            0L
        );
    }
}