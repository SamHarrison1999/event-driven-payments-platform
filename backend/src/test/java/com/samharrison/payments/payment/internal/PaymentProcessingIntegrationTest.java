package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.shared.GbpAmount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(
    PaymentProcessingIntegrationTest
        .ClockConfiguration.class
)
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class PaymentProcessingIntegrationTest {

    private static final Instant ACCOUNT_TIME =
        Instant.parse(
            "2026-07-03T10:00:00Z"
        );

    private static final Instant RESERVED_AT =
        Instant.parse(
            "2026-07-03T12:00:00.123456Z"
        );

    private static final Instant PROCESSED_AT =
        Instant.parse(
            "2026-07-03T12:00:30.654321Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_processing_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private PaymentReservationCoordinator
        reservationCoordinator;

    @Autowired
    private PaymentProcessingCoordinator
        processingCoordinator;

    @Autowired
    private PaymentFailureFinalizer
        failureFinalizer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentIdempotencyRecordRepository
        idempotencyRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabaseAndResetClock() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                payment_idempotency,
                payment,
                ledger_entry,
                ledger_transaction
            """
        );

        jdbcTemplate.update(
            "DELETE FROM customer_identity_assignment"
        );

        jdbcTemplate.update(
            "DELETE FROM customer_account"
        );

        jdbcTemplate.update(
            "DELETE FROM customer_profile"
        );

        clock.setInstant(RESERVED_AT);
    }

    @Test
    void approvedPaymentCommitsBalancesLedgerAndResponse() {
        PaymentFixture fixture =
            paymentFixture(
                1_000L,
                250L,
                400L
            );

        PaymentReservationResult.Acquired acquired =
            reserve(
                fixture,
                "processing-approved"
            );

        clock.setInstant(PROCESSED_AT);

        StoredPaymentResponse response =
            processingCoordinator.process(
                acquired.paymentId(),
                acquired.ownerToken()
            );

        Payment payment =
            paymentRepository
                .findById(acquired.paymentId())
                .orElseThrow();

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.COMPLETED);

        assertThat(payment.ledgerTransactionId())
            .isNotNull();

        assertThat(payment.rejectionReason())
            .isNull();

        assertThat(payment.failureReason())
            .isNull();

        assertThat(response.status())
            .isEqualTo(201);

        assertThat(response.mediaType())
            .isEqualTo(
                StoredPaymentResponse
                    .APPLICATION_JSON
            );

        assertThat(response.body())
            .isEqualTo(
                """
                {"paymentId":"%s","status":"COMPLETED","ledgerTransactionId":"%s"}
                """
                    .strip()
                    .formatted(
                        payment.id(),
                        payment.ledgerTransactionId()
                    )
            );

        assertThat(
            accountState(fixture.sourceAccountId())
        )
            .isEqualTo(
                new AccountState(
                    600L,
                    PROCESSED_AT,
                    1L
                )
            );

        assertThat(
            accountState(
                fixture.destinationAccountId()
            )
        )
            .isEqualTo(
                new AccountState(
                    650L,
                    PROCESSED_AT,
                    1L
                )
            );

        assertThat(
            countRows("ledger_transaction")
        )
            .isEqualTo(1L);

        assertThat(
            countRows("ledger_entry")
        )
            .isEqualTo(2L);

        assertThat(
            ledgerTransactionType(
                payment.ledgerTransactionId()
            )
        )
            .isEqualTo("INTERNAL_PAYMENT");

        assertThat(
            ledgerBusinessReference(
                payment.ledgerTransactionId()
            )
        )
            .isEqualTo(payment.id().toString());

        assertStoredResponse(
            payment.id(),
            response
        );

        assertReplay(
            fixture,
            "processing-approved",
            response
        );
    }

    @Test
    void rejectedPaymentMakesNoFinancialChanges() {
        PaymentFixture fixture =
            paymentFixture(
                50L,
                25L,
                100L
            );

        PaymentReservationResult.Acquired acquired =
            reserve(
                fixture,
                "processing-rejected"
            );

        clock.setInstant(PROCESSED_AT);

        StoredPaymentResponse response =
            processingCoordinator.process(
                acquired.paymentId(),
                acquired.ownerToken()
            );

        Payment payment =
            paymentRepository
                .findById(acquired.paymentId())
                .orElseThrow();

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.REJECTED);

        assertThat(payment.rejectionReason())
            .isEqualTo(
                PaymentRejectionReason
                    .INSUFFICIENT_FUNDS
            );

        assertThat(payment.ledgerTransactionId())
            .isNull();

        assertThat(response)
            .isEqualTo(
                PaymentResponseFactory.rejected(
                    payment.id(),
                    PaymentRejectionReason
                        .INSUFFICIENT_FUNDS
                )
            );

        assertThat(
            accountState(fixture.sourceAccountId())
        )
            .isEqualTo(
                new AccountState(
                    50L,
                    ACCOUNT_TIME,
                    0L
                )
            );

        assertThat(
            accountState(
                fixture.destinationAccountId()
            )
        )
            .isEqualTo(
                new AccountState(
                    25L,
                    ACCOUNT_TIME,
                    0L
                )
            );

        assertThat(
            countRows("ledger_transaction")
        )
            .isZero();

        assertThat(
            countRows("ledger_entry")
        )
            .isZero();

        assertStoredResponse(
            payment.id(),
            response
        );

        assertReplay(
            fixture,
            "processing-rejected",
            response
        );
    }

    @Test
    void unknownSourceBecomesReplayableRejection() {
        UUID actorId = insertIdentityUser();

        UUID sourceCustomerId =
            insertCustomer("Unknown Source Owner");

        insertOwnership(
            actorId,
            sourceCustomerId
        );

        UUID destinationCustomerId =
            insertCustomer(
                "Known Destination Customer"
            );

        UUID destinationAccountId =
            insertAccount(
                destinationCustomerId,
                10L
            );

        PaymentFixture fixture =
            new PaymentFixture(
                actorId,
                UUID.randomUUID(),
                destinationAccountId,
                GbpAmount.ofMinorUnits(5L)
            );

        PaymentReservationResult.Acquired acquired =
            reserve(
                fixture,
                "processing-unknown-source"
            );

        clock.setInstant(PROCESSED_AT);

        StoredPaymentResponse response =
            processingCoordinator.process(
                acquired.paymentId(),
                acquired.ownerToken()
            );

        Payment payment =
            paymentRepository
                .findById(acquired.paymentId())
                .orElseThrow();

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.REJECTED);

        assertThat(payment.rejectionReason())
            .isEqualTo(
                PaymentRejectionReason
                    .SOURCE_NOT_FOUND
            );

        assertThat(response)
            .isEqualTo(
                PaymentResponseFactory.rejected(
                    payment.id(),
                    PaymentRejectionReason
                        .SOURCE_NOT_FOUND
                )
            );

        assertThat(
            countRows("ledger_transaction")
        )
            .isZero();

        assertStoredResponse(
            payment.id(),
            response
        );

        assertReplay(
            fixture,
            "processing-unknown-source",
            response
        );
    }

    @Test
    void failureFinalisationStoresStableProblem() {
        PaymentFixture fixture =
            paymentFixture(
                1_000L,
                250L,
                400L
            );

        PaymentReservationResult.Acquired acquired =
            reserve(
                fixture,
                "processing-failed"
            );

        clock.setInstant(PROCESSED_AT);

        StoredPaymentResponse response =
            failureFinalizer.finalizeFailure(
                acquired.paymentId(),
                acquired.ownerToken(),
                PaymentFailureReason
                    .PROCESSING_FAILED
            );

        Payment payment =
            paymentRepository
                .findById(acquired.paymentId())
                .orElseThrow();

        assertThat(payment.status())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.failureReason())
            .isEqualTo(
                PaymentFailureReason
                    .PROCESSING_FAILED
            );

        assertThat(payment.ledgerTransactionId())
            .isNull();

        assertThat(response)
            .isEqualTo(
                PaymentResponseFactory.failed(
                    payment.id(),
                    PaymentFailureReason
                        .PROCESSING_FAILED
                )
            );

        assertThat(response.body())
            .doesNotContain(
                "database",
                "exception",
                "stack"
            );

        assertThat(
            accountState(fixture.sourceAccountId())
        )
            .isEqualTo(
                new AccountState(
                    1_000L,
                    ACCOUNT_TIME,
                    0L
                )
            );

        assertThat(
            accountState(
                fixture.destinationAccountId()
            )
        )
            .isEqualTo(
                new AccountState(
                    250L,
                    ACCOUNT_TIME,
                    0L
                )
            );

        assertThat(
            countRows("ledger_transaction")
        )
            .isZero();

        assertStoredResponse(
            payment.id(),
            response
        );

        assertReplay(
            fixture,
            "processing-failed",
            response
        );
    }

    private PaymentReservationResult.Acquired reserve(
        PaymentFixture fixture,
        String key
    ) {
        PaymentReservationResult result =
            reservationCoordinator.reserve(
                fixture.actorId(),
                IdempotencyKey.of(key),
                fixture.request()
            );

        assertThat(result)
            .isInstanceOf(
                PaymentReservationResult
                    .Acquired.class
            );

        return (PaymentReservationResult.Acquired)
            result;
    }

    private void assertReplay(
        PaymentFixture fixture,
        String key,
        StoredPaymentResponse response
    ) {
        PaymentReservationResult replay =
            reservationCoordinator.reserve(
                fixture.actorId(),
                IdempotencyKey.of(key),
                fixture.request()
            );

        assertThat(replay)
            .isEqualTo(
                new PaymentReservationResult.Replay(
                    response
                )
            );
    }

    private void assertStoredResponse(
        UUID paymentId,
        StoredPaymentResponse response
    ) {
        PaymentIdempotencyRecord completed =
            idempotencyRepository
                .findByPaymentId(paymentId)
                .orElseThrow();

        assertThat(completed.status())
            .isEqualTo(
                PaymentIdempotencyStatus
                    .COMPLETED
            );

        assertThat(completed.storedResponse())
            .contains(response);

        assertThat(completed.retentionExpiresAt())
            .isEqualTo(
                PROCESSED_AT.plus(
                    PaymentIdempotencyRecord
                        .TERMINAL_RETENTION
                )
            );
    }

    private PaymentFixture paymentFixture(
        long sourceBalance,
        long destinationBalance,
        long amount
    ) {
        UUID actorId = insertIdentityUser();

        UUID sourceCustomerId =
            insertCustomer("Source Customer");

        UUID destinationCustomerId =
            insertCustomer(
                "Destination Customer"
            );

        insertOwnership(
            actorId,
            sourceCustomerId
        );

        UUID sourceAccountId =
            insertAccount(
                sourceCustomerId,
                sourceBalance
            );

        UUID destinationAccountId =
            insertAccount(
                destinationCustomerId,
                destinationBalance
            );

        return new PaymentFixture(
            actorId,
            sourceAccountId,
            destinationAccountId,
            GbpAmount.ofMinorUnits(amount)
        );
    }

    private UUID insertIdentityUser() {
        UUID identityUserId =
            UUID.randomUUID();

        String email =
            identityUserId
                + "@processing.test";

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
            "processing-test-password-hash",
            "ACTIVE",
            0,
            null,
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return identityUserId;
    }

    private UUID insertCustomer(
        String fullName
    ) {
        UUID customerId =
            UUID.randomUUID();

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
            fullName,
            "ACTIVE",
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return customerId;
    }

    private void insertOwnership(
        UUID identityUserId,
        UUID customerId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_identity_assignment (
                identity_user_id,
                customer_id,
                assigned_at,
                version
            )
            VALUES (?, ?, ?, ?)
            """,
            identityUserId,
            customerId,
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );
    }

    private UUID insertAccount(
        UUID customerId,
        long balanceMinorUnits
    ) {
        UUID accountId =
            UUID.randomUUID();

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
            balanceMinorUnits,
            "ACTIVE",
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return accountId;
    }

    private AccountState accountState(
        UUID accountId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                balance_minor_units,
                updated_at,
                version
            FROM customer_account
            WHERE id = ?
            """,
            (
                resultSet,
                rowNumber
            ) ->
                toAccountState(resultSet),
            accountId
        );
    }

    private static AccountState toAccountState(
        ResultSet resultSet
    ) throws SQLException {
        return new AccountState(
            resultSet.getLong(
                "balance_minor_units"
            ),
            resultSet
                .getObject(
                    "updated_at",
                    OffsetDateTime.class
                )
                .toInstant(),
            resultSet.getLong("version")
        );
    }

    private Long countRows(
        String tableName
    ) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName,
            Long.class
        );
    }

    private String ledgerTransactionType(
        UUID transactionId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT transaction_type
            FROM ledger_transaction
            WHERE id = ?
            """,
            String.class,
            transactionId
        );
    }

    private String ledgerBusinessReference(
        UUID transactionId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT business_reference
            FROM ledger_transaction
            WHERE id = ?
            """,
            String.class,
            transactionId
        );
    }

    private record PaymentFixture(
        UUID actorId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        GbpAmount amount
    ) {

        private PaymentRequestData request() {
            return new PaymentRequestData(
                sourceAccountId,
                destinationAccountId,
                amount
            );
        }
    }

    private record AccountState(
        long balanceMinorUnits,
        Instant updatedAt,
        long version
    ) {
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock paymentProcessingClock() {
            return new MutableClock(
                RESERVED_AT
            );
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant>
            currentInstant;

        private MutableClock(
            Instant initialInstant
        ) {
            currentInstant =
                new AtomicReference<>(
                    initialInstant
                );
        }

        void setInstant(
            Instant instant
        ) {
            currentInstant.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(
            ZoneId zone
        ) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException(
                    "Only UTC is supported."
                );
            }

            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }
    }
}
