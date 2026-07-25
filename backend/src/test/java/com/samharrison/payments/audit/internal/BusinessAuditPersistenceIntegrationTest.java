package com.samharrison.payments.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.audit.BusinessAuditActor;
import com.samharrison.payments.audit.BusinessAuditEventConflictException;
import com.samharrison.payments.audit.BusinessAuditEventRequest;
import com.samharrison.payments.audit.BusinessAuditEventType;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.audit.InvalidBusinessAuditEventException;
import com.samharrison.payments.audit.RecordedBusinessAuditEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class BusinessAuditPersistenceIntegrationTest {

    private static final Instant OCCURRED_AT =
        Instant.parse("2026-07-24T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "business_audit_test"
            )
            .withUsername(
                "business_audit_test"
            )
            .withPassword(
                "business_audit_test_only"
            );

    @Autowired
    private BusinessAuditRecorder recorder;

    @Autowired
    private BusinessAuditEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearStoredData() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                business_audit_event,
                customer_profile
            CASCADE
            """
        );
    }

    @Test
    void appliesBusinessAuditMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '19'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount).isEqualTo(1L);
    }

    @Test
    void recordsCanonicalImmutableContent() {
        BusinessAuditEventRequest request =
            completedPaymentRequest(
                "payment-completed-1",
                1250L
            );

        RecordedBusinessAuditEvent recorded =
            record(request);

        assertThat(recorded.existing()).isFalse();

        BusinessAuditEvent event =
            repository
                .findById(recorded.eventId())
                .orElseThrow();

        assertThat(event.eventType())
            .isEqualTo("payment.completed");
        assertThat(event.schemaVersion())
            .isEqualTo(1);
        assertThat(event.occurredAt())
            .isEqualTo(OCCURRED_AT);
        assertThat(event.recordedAt())
            .isAfterOrEqualTo(OCCURRED_AT);
        assertThat(event.actorKind().name())
            .isEqualTo("SYSTEM");
        assertThat(event.actorIdentityUserId())
            .isNull();
        assertThat(event.subjectType())
            .isEqualTo("payment");
        assertThat(event.sourceModule())
            .isEqualTo("payment");
        assertThat(event.sourceRecordType())
            .isEqualTo("payment");
        assertThat(event.metadata())
            .isEqualTo(
                """
                {"amountMinor":1250,"currency":"GBP"}\
                """
            );
    }

    @Test
    void retryReturnsExistingEventIdentifier() {
        BusinessAuditEventRequest request =
            completedPaymentRequest(
                "payment-completed-1",
                1250L
            );

        RecordedBusinessAuditEvent first =
            record(request);
        RecordedBusinessAuditEvent retry =
            record(request);

        assertThat(retry.eventId())
            .isEqualTo(first.eventId());
        assertThat(retry.existing()).isTrue();
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void distinctOccurrencesForOneRecordArePreserved() {
        BusinessAuditEventRequest firstRequest =
            completedPaymentRequest(
                "payment-completed-1",
                1250L
            );

        BusinessAuditEventRequest secondRequest =
            completedPaymentRequest(
                "payment-completed-2",
                1250L
            );

        RecordedBusinessAuditEvent first =
            record(firstRequest);
        RecordedBusinessAuditEvent second =
            record(secondRequest);

        assertThat(second.eventId())
            .isNotEqualTo(first.eventId());
        assertThat(repository.count()).isEqualTo(2L);
    }

    @Test
    void conflictingOccurrenceContentIsRejected() {
        record(
            completedPaymentRequest(
                "payment-completed-1",
                1250L
            )
        );

        assertThatThrownBy(
            () ->
                record(
                    completedPaymentRequest(
                        "payment-completed-1",
                        2500L
                    )
                )
        )
            .isInstanceOf(
                BusinessAuditEventConflictException.class
            )
            .hasMessageContaining(
                "payment-completed-1"
            );

        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void recordingRequiresAnExistingTransaction() {
        assertThatThrownBy(
            () ->
                recorder.record(
                    completedPaymentRequest(
                        "payment-completed-1",
                        1250L
                    )
                )
        )
            .isInstanceOf(
                IllegalTransactionStateException.class
            );
    }

    @Test
    void rejectsMetadataOutsideVersionedSchema() {
        String paymentId =
            UUID.randomUUID().toString();

        BusinessAuditEventRequest request =
            new BusinessAuditEventRequest(                BusinessAuditEventType
                    .PAYMENT_COMPLETED,
                OCCURRED_AT,
                BusinessAuditActor.system(),
                paymentId,
                paymentId,
                "payment-completed-1",
                "correlation-1",
                Map.of(
                    "amountMinor",
                    1250L,
                    "currency",
                    "GBP",
                    "unexpected",
                    "value"
                )
            );

        assertThatThrownBy(
            () -> record(request)
        )
            .isInstanceOf(
                InvalidBusinessAuditEventException.class
            )
            .hasMessageContaining(
                "versioned event schema"
            );

        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsMismatchedSourceAndSubject() {
        BusinessAuditEventRequest request =
            new BusinessAuditEventRequest(
                BusinessAuditEventType
                    .PAYMENT_COMPLETED,
                OCCURRED_AT,
                BusinessAuditActor.system(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "payment-completed-1",
                "correlation-1",
                Map.of(
                    "amountMinor",
                    1250L,
                    "currency",
                    "GBP"
                )
            );

        assertThatThrownBy(
            () -> record(request)
        )
            .isInstanceOf(
                InvalidBusinessAuditEventException.class
            )
            .hasMessageContaining(
                "source record"
            );

        assertThat(repository.count()).isZero();
    }

    @Test
    void databaseRejectsMetadataOutsideSchema() {
        assertThatThrownBy(
            () ->
                insertDirectly(
                    UUID.randomUUID(),
                    "SYSTEM",
                    null,
                    """
                    {
                      "amountMinor": 1250,
                      "currency": "GBP",
                      "unexpected": "value"
                    }
                    """
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "ck_business_audit_event_metadata"
            );
    }

    @Test
    void databaseRejectsInvalidActorPairing() {
        assertThatThrownBy(
            () ->
                insertDirectly(
                    UUID.randomUUID(),
                    "SYSTEM",
                    UUID.randomUUID(),
                    """
                    {
                      "amountMinor": 1250,
                      "currency": "GBP"
                    }
                    """
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "ck_business_audit_event_actor"
            );
    }

    @Test
    void databaseRejectsUpdateAndDelete() {
        UUID eventId =
            record(
                completedPaymentRequest(
                    "payment-completed-1",
                    1250L
                )
            )
                .eventId();

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE business_audit_event
                    SET correlation_identifier = ?
                    WHERE id = ?
                    """,
                    "changed-correlation",
                    eventId
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "business_audit_event is immutable"
            );

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    DELETE FROM business_audit_event
                    WHERE id = ?
                    """,
                    eventId
                )
        )
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining(
                "business_audit_event is immutable"
            );
    }

    @Test
    void auditFailureRollsBackSurroundingMutation() {
        BusinessAuditEventRequest original =
            completedPaymentRequest(
                "payment-completed-1",
                1250L
            );

        record(original);

        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(
            () ->
                transactionTemplate
                    .executeWithoutResult(
                        status -> {
                            insertCustomer(customerId);

                            recorder.record(
                                completedPaymentRequest(
                                    "payment-completed-1",
                                    2500L
                                )
                            );
                        }
                    )
        )
            .isInstanceOf(
                BusinessAuditEventConflictException.class
            );

        Long customerCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM customer_profile
                WHERE id = ?
                """,
                Long.class,
                customerId
            );

        assertThat(customerCount).isZero();
        assertThat(repository.count()).isEqualTo(1L);
    }

    private RecordedBusinessAuditEvent record(
        BusinessAuditEventRequest request
    ) {
        return transactionTemplate.execute(
            status -> recorder.record(request)
        );
    }

    private static BusinessAuditEventRequest
        completedPaymentRequest(
            String sourceEventIdentifier,
            long amountMinor
        ) {
        String paymentId =
            "8d67b66f-d903-44f4-a04c-430353555e70";

        return new BusinessAuditEventRequest(
            BusinessAuditEventType.PAYMENT_COMPLETED,
            OCCURRED_AT,
            BusinessAuditActor.system(),
            paymentId,
            paymentId,
            sourceEventIdentifier,
            "correlation-1",
            Map.of(
                "amountMinor",
                amountMinor,
                "currency",
                "GBP"
            )
        );
    }

    private void insertDirectly(
        UUID eventId,
        String actorKind,
        UUID actorIdentityUserId,
        String metadata
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO business_audit_event (
                id,
                event_type,
                schema_version,
                occurred_at,
                recorded_at,
                actor_kind,
                actor_identity_user_id,
                subject_type,
                subject_identifier,
                source_module,
                source_record_type,
                source_record_identifier,
                source_event_identifier,
                correlation_identifier,
                metadata
            )
            VALUES (
                ?, 'payment.completed', 1, ?, ?,
                ?, ?, 'payment', ?, 'payment',
                'payment', ?, ?, 'correlation-direct',
                ?
            )
            """,
            eventId,
            Timestamp.from(OCCURRED_AT),
            Timestamp.from(OCCURRED_AT),
            actorKind,
            actorIdentityUserId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            metadata
        );
    }

    private void insertCustomer(
        UUID customerId
    ) {
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
            VALUES (?, ?, 'ACTIVE', ?, ?, 0)
            """,
            customerId,
            "Audit rollback probe",
            Timestamp.from(OCCURRED_AT),
            Timestamp.from(OCCURRED_AT)
        );
    }
}
