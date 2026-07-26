package com.samharrison.payments.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessAuditEventRequestTest {

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-24T10:15:30.123456789Z"
        );

    @Test
    void normalizesTimeNumbersAndMetadataOrder() {
        Map<String, Object> metadata =
            new LinkedHashMap<>();

        metadata.put("currency", "GBP");
        metadata.put("amountMinor", 1250);

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
                metadata
            );

        metadata.put("unexpected", "value");

        assertThat(request.occurredAt())
            .isEqualTo(
                Instant.parse(
                    "2026-07-24T10:15:30.123456Z"
                )
            );
        assertThat(request.metadata())
            .containsExactly(
                Map.entry("amountMinor", 1250L),
                Map.entry("currency", "GBP")
            );
    }

    @Test
    void rejectsIdentityActorWithoutIdentity() {
        assertThatThrownBy(
            () ->
                new BusinessAuditActor(
                    BusinessAuditActorKind.IDENTITY_USER,
                    null
                )
        )
            .isInstanceOf(
                InvalidBusinessAuditEventException.class
            )
            .hasMessageContaining(
                "requires an identity"
            );
    }

    @Test
    void rejectsSystemActorWithIdentity() {
        assertThatThrownBy(
            () ->
                new BusinessAuditActor(
                    BusinessAuditActorKind.SYSTEM,
                    UUID.randomUUID()
                )
        )
            .isInstanceOf(
                InvalidBusinessAuditEventException.class
            )
            .hasMessageContaining(
                "cannot have an identity"
            );
    }

    @Test
    void rejectsUnboundedMetadataValue() {
        assertThatThrownBy(
            () ->
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
                        100L,
                        "nested",
                        Map.of("secret", "value")
                    )
                )
        )
            .isInstanceOf(
                InvalidBusinessAuditEventException.class
            )
            .hasMessageContaining(
                "bounded strings, integers or booleans"
            );
    }

    @Test
    void rejectsUnsafeCorrelationIdentifier() {
        assertThatThrownBy(
            () ->
                new BusinessAuditEventRequest(
                    BusinessAuditEventType
                        .PAYMENT_COMPLETED,
                    OCCURRED_AT,
                    BusinessAuditActor.system(),
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "payment-completed-1",
                    "correlation with spaces",
                    Map.of(
                        "amountMinor",
                        100L,
                        "currency",
                        "GBP"
                    )
                )
        )
            .isInstanceOf(
                InvalidBusinessAuditEventException.class
            )
            .hasMessageContaining(
                "correlationIdentifier"
            );
    }
}
