package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Clock;
import java.time.Instant;
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
    PaymentReservationCoordinatorIntegrationTest
        .ClockConfiguration.class
)
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class PaymentReservationCoordinatorIntegrationTest {

    private static final Instant RESERVED_AT =
        Instant.parse(
            "2026-07-03T12:00:00.123456Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_reservation_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private PaymentReservationCoordinator coordinator;

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
    void resetClock() {
        clock.setInstant(RESERVED_AT);
    }

    @Test
    void newKeyCreatesPendingPaymentAndReservation() {
        UUID actorId = insertIdentityUser();

        PaymentRequestData request = request(1_250L);

        PaymentReservationResult result =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of("reservation-new"),
                request
            );

        assertThat(result)
            .isInstanceOfSatisfying(
                PaymentReservationResult
                    .Acquired.class,
                acquired -> {
                    Payment payment =
                        paymentRepository
                            .findById(
                                acquired.paymentId()
                            )
                            .orElseThrow();

                    assertThat(
                        payment.actorIdentityId()
                    )
                        .isEqualTo(actorId);

                    assertThat(payment.request())
                        .isEqualTo(request);

                    assertThat(payment.status())
                        .isEqualTo(
                            PaymentStatus.PENDING
                        );

                    PaymentIdempotencyRecord
                        reservation =
                            findReservation(
                                actorId,
                                "reservation-new"
                            );

                    assertThat(
                        reservation.paymentId()
                    )
                        .isEqualTo(payment.id());

                    assertThat(
                        reservation
                            .processingOwnerToken()
                    )
                        .isEqualTo(
                            acquired.ownerToken()
                        );

                    assertThat(
                        reservation
                            .processingLeaseExpiresAt()
                    )
                        .isEqualTo(
                            RESERVED_AT.plus(
                                PaymentIdempotencyRecord
                                    .PROCESSING_LEASE
                            )
                        );
                }
            );

        assertPaymentCount(actorId, 1L);
    }

    @Test
    void activeReservationReturnsInProgress() {
        UUID actorId = insertIdentityUser();

        PaymentRequestData request = request(1_250L);

        PaymentReservationResult first =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of("reservation-active"),
                request
            );

        PaymentReservationResult.Acquired acquired =
            (PaymentReservationResult.Acquired)
                first;

        PaymentReservationResult second =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of("reservation-active"),
                request
            );

        assertThat(second)
            .isEqualTo(
                new PaymentReservationResult.Conflict(
                    PaymentReservationResult
                        .Reason
                        .IDEMPOTENCY_REQUEST_IN_PROGRESS
                )
            );

        PaymentIdempotencyRecord reservation =
            findReservation(
                actorId,
                "reservation-active"
            );

        assertThat(reservation.paymentId())
            .isEqualTo(acquired.paymentId());

        assertThat(
            reservation.processingOwnerToken()
        )
            .isEqualTo(acquired.ownerToken());

        assertPaymentCount(actorId, 1L);
    }

    @Test
    void differentFingerprintReturnsKeyReused() {
        UUID actorId = insertIdentityUser();

        coordinator.reserve(
            actorId,
            IdempotencyKey.of("reservation-reused"),
            request(1_250L)
        );

        PaymentReservationResult result =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of("reservation-reused"),
                request(1_251L)
            );

        assertThat(result)
            .isEqualTo(
                new PaymentReservationResult.Conflict(
                    PaymentReservationResult
                        .Reason
                        .IDEMPOTENCY_KEY_REUSED
                )
            );

        assertPaymentCount(actorId, 1L);
    }

    @Test
    void completedReservationReplaysExactResponse() {
        UUID actorId = insertIdentityUser();

        PaymentRequestData request = request(1_250L);

        PaymentReservationResult.Acquired acquired =
            (PaymentReservationResult.Acquired)
                coordinator.reserve(
                    actorId,
                    IdempotencyKey.of(
                        "reservation-replay"
                    ),
                    request
                );

        Instant processingAt =
            RESERVED_AT.plusSeconds(10L);

        Payment payment =
            paymentRepository
                .findById(acquired.paymentId())
                .orElseThrow();

        payment.startProcessing(processingAt);

        Instant rejectedAt =
            RESERVED_AT.plusSeconds(20L);

        payment.reject(
            PaymentRejectionReason
                .INSUFFICIENT_FUNDS,
            rejectedAt
        );

        paymentRepository.saveAndFlush(payment);

        StoredPaymentResponse response =
            new StoredPaymentResponse(
                422,
                StoredPaymentResponse
                    .APPLICATION_PROBLEM_JSON,
                """
                {"type":"urn:problem:payment:insufficient-funds","title":"Payment rejected","status":422,"detail":"The source account has insufficient funds.","code":"PAYMENT_INSUFFICIENT_FUNDS"}
                """
                    .trim()
            );

        PaymentIdempotencyRecord reservation =
            findReservation(
                actorId,
                "reservation-replay"
            );

        reservation.complete(
            acquired.ownerToken(),
            response,
            rejectedAt
        );

        idempotencyRepository
            .saveAndFlush(reservation);

        clock.setInstant(
            RESERVED_AT.plusSeconds(30L)
        );

        PaymentReservationResult replay =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of(
                    "reservation-replay"
                ),
                request
            );

        assertThat(replay)
            .isEqualTo(
                new PaymentReservationResult.Replay(
                    response
                )
            );

        assertPaymentCount(actorId, 1L);
    }

    @Test
    void expiredReservationIsReclaimed() {
        UUID actorId = insertIdentityUser();

        PaymentRequestData request = request(1_250L);

        PaymentReservationResult.Acquired original =
            (PaymentReservationResult.Acquired)
                coordinator.reserve(
                    actorId,
                    IdempotencyKey.of(
                        "reservation-expired"
                    ),
                    request
                );

        Instant reclaimedAt =
            RESERVED_AT.plus(
                PaymentIdempotencyRecord
                    .PROCESSING_LEASE
            );

        clock.setInstant(reclaimedAt);

        PaymentReservationResult result =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of(
                    "reservation-expired"
                ),
                request
            );

        assertThat(result)
            .isInstanceOfSatisfying(
                PaymentReservationResult
                    .Acquired.class,
                reclaimed -> {
                    assertThat(
                        reclaimed.paymentId()
                    )
                        .isEqualTo(
                            original.paymentId()
                        );

                    assertThat(
                        reclaimed.ownerToken()
                    )
                        .isNotEqualTo(
                            original.ownerToken()
                        );

                    PaymentIdempotencyRecord
                        reservation =
                            findReservation(
                                actorId,
                                "reservation-expired"
                            );

                    assertThat(
                        reservation
                            .processingOwnerToken()
                    )
                        .isEqualTo(
                            reclaimed.ownerToken()
                        );

                    assertThat(
                        reservation
                            .processingLeaseExpiresAt()
                    )
                        .isEqualTo(
                            reclaimedAt.plus(
                                PaymentIdempotencyRecord
                                    .PROCESSING_LEASE
                            )
                        );

                    assertThat(
                        reservation.version()
                    )
                        .isEqualTo(1L);
                }
            );

        assertPaymentCount(actorId, 1L);
    }

    @Test
    void differentFingerprintCannotReclaimExpiredKey() {
        UUID actorId = insertIdentityUser();

        PaymentReservationResult.Acquired original =
            (PaymentReservationResult.Acquired)
                coordinator.reserve(
                    actorId,
                    IdempotencyKey.of(
                        "reservation-expired-reused"
                    ),
                    request(1_250L)
                );

        clock.setInstant(
            RESERVED_AT.plus(
                PaymentIdempotencyRecord
                    .PROCESSING_LEASE
            )
        );

        PaymentReservationResult result =
            coordinator.reserve(
                actorId,
                IdempotencyKey.of(
                    "reservation-expired-reused"
                ),
                request(1_251L)
            );

        assertThat(result)
            .isEqualTo(
                new PaymentReservationResult.Conflict(
                    PaymentReservationResult
                        .Reason
                        .IDEMPOTENCY_KEY_REUSED
                )
            );

        PaymentIdempotencyRecord reservation =
            findReservation(
                actorId,
                "reservation-expired-reused"
            );

        assertThat(
            reservation.processingOwnerToken()
        )
            .isEqualTo(original.ownerToken());

        assertThat(reservation.version())
            .isZero();

        assertPaymentCount(actorId, 1L);
    }

    private PaymentIdempotencyRecord findReservation(
        UUID actorId,
        String key
    ) {
        return idempotencyRepository
            .findByActorIdentityIdAndOperationAndIdempotencyKey(
                actorId,
                PaymentOperation
                    .CREATE_INTERNAL_PAYMENT,
                key
            )
            .orElseThrow();
    }

    private void assertPaymentCount(
        UUID actorId,
        long expected
    ) {
        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment
                WHERE actor_identity_id = ?
                """,
                Long.class,
                actorId
            );

        assertThat(count)
            .isEqualTo(expected);
    }

    private PaymentRequestData request(
        long amountMinorUnits
    ) {
        return new PaymentRequestData(
            UUID.fromString(
                "30000000-0000-0000-0000-000000000001"
            ),
            UUID.fromString(
                "30000000-0000-0000-0000-000000000002"
            ),
            GbpAmount.ofMinorUnits(
                amountMinorUnits
            )
        );
    }

    private UUID insertIdentityUser() {
        UUID identityUserId =
            UUID.randomUUID();

        String email =
            identityUserId
                + "@reservation.test";

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
            "reservation-test-password-hash",
            "ACTIVE",
            0,
            null,
            RESERVED_AT.atOffset(
                ZoneOffset.UTC
            ),
            RESERVED_AT.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return identityUserId;
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock paymentReservationClock() {
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
            currentInstant.set(
                instant
            );
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
