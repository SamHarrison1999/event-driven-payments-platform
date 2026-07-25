package com.samharrison.payments.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class BusinessAuditEvents {

    private static final String GBP = "GBP";

    private BusinessAuditEvents() {
    }

    public static BusinessAuditEventRequest customerCreated(
        Instant occurredAt,
        UUID actorIdentityUserId,
        UUID customerId
    ) {
        return request(
            BusinessAuditEventType.CUSTOMER_CREATED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            customerId,
            customerId,
            "created",
            customerId,
            Map.of("status", "ACTIVE")
        );
    }

    public static BusinessAuditEventRequest
        customerStatusChanged(
            Instant occurredAt,
            UUID actorIdentityUserId,
            UUID customerId,
            String previousStatus,
            String newStatus,
            long version
        ) {
        return request(
            BusinessAuditEventType
                .CUSTOMER_STATUS_CHANGED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            customerId,
            customerId,
            versionIdentifier(version),
            customerId,
            Map.of(
                "previousStatus",
                previousStatus,
                "newStatus",
                newStatus
            )
        );
    }

    public static BusinessAuditEventRequest accountCreated(
        Instant occurredAt,
        UUID actorIdentityUserId,
        UUID accountId,
        UUID customerId
    ) {
        return request(
            BusinessAuditEventType.ACCOUNT_CREATED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            accountId,
            accountId,
            "created",
            accountId,
            Map.of(
                "customerId",
                customerId.toString(),
                "currency",
                GBP,
                "status",
                "ACTIVE"
            )
        );
    }

    public static BusinessAuditEventRequest
        accountStatusChanged(
            Instant occurredAt,
            UUID actorIdentityUserId,
            UUID accountId,
            String previousStatus,
            String newStatus,
            long version
        ) {
        return request(
            BusinessAuditEventType
                .ACCOUNT_STATUS_CHANGED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            accountId,
            accountId,
            versionIdentifier(version),
            accountId,
            Map.of(
                "previousStatus",
                previousStatus,
                "newStatus",
                newStatus
            )
        );
    }

    public static BusinessAuditEventRequest
        identityCustomerAssigned(
            Instant occurredAt,
            UUID actorIdentityUserId,
            UUID identityUserId,
            UUID customerId
        ) {
        return request(
            BusinessAuditEventType
                .IDENTITY_CUSTOMER_ASSIGNED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            customerId,
            identityUserId,
            "assigned",
            customerId,
            Map.of(
                "customerId",
                customerId.toString()
            )
        );
    }

    public static BusinessAuditEventRequest paymentSubmitted(
        Instant occurredAt,
        UUID actorIdentityUserId,
        UUID paymentId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountMinor
    ) {
        return request(
            BusinessAuditEventType.PAYMENT_SUBMITTED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            paymentId,
            paymentId,
            "submitted",
            paymentId,
            Map.of(
                "sourceAccountId",
                sourceAccountId.toString(),
                "destinationAccountId",
                destinationAccountId.toString(),
                "amountMinor",
                amountMinor,
                "currency",
                GBP
            )
        );
    }

    public static BusinessAuditEventRequest paymentCompleted(
        Instant occurredAt,
        UUID paymentId,
        long amountMinor
    ) {
        return request(
            BusinessAuditEventType.PAYMENT_COMPLETED,
            occurredAt,
            BusinessAuditActor.system(),
            paymentId,
            paymentId,
            "completed",
            paymentId,
            Map.of(
                "amountMinor",
                amountMinor,
                "currency",
                GBP
            )
        );
    }

    public static BusinessAuditEventRequest paymentRejected(
        Instant occurredAt,
        UUID paymentId,
        String reasonCode
    ) {
        return request(
            BusinessAuditEventType.PAYMENT_REJECTED,
            occurredAt,
            BusinessAuditActor.system(),
            paymentId,
            paymentId,
            "rejected",
            paymentId,
            Map.of("reasonCode", reasonCode)
        );
    }

    public static BusinessAuditEventRequest paymentFailed(
        Instant occurredAt,
        UUID paymentId,
        String failureCode
    ) {
        return request(
            BusinessAuditEventType.PAYMENT_FAILED,
            occurredAt,
            BusinessAuditActor.system(),
            paymentId,
            paymentId,
            "failed",
            paymentId,
            Map.of("failureCode", failureCode)
        );
    }

    public static BusinessAuditEventRequest
        settlementImportAccepted(
            Instant occurredAt,
            UUID actorIdentityUserId,
            UUID importId,
            long rowCount,
            long matchedCount,
            long discrepancyCount
        ) {
        return request(
            BusinessAuditEventType
                .SETTLEMENT_IMPORT_ACCEPTED,
            occurredAt,
            BusinessAuditActor.identityUser(
                actorIdentityUserId
            ),
            importId,
            importId,
            "accepted",
            importId,
            Map.of(
                "rowCount",
                rowCount,
                "matchedCount",
                matchedCount,
                "discrepancyCount",
                discrepancyCount
            )
        );
    }

    private static BusinessAuditEventRequest request(
        BusinessAuditEventType eventType,
        Instant occurredAt,
        BusinessAuditActor actor,
        UUID subjectIdentifier,
        UUID sourceRecordIdentifier,
        String sourceEventIdentifier,
        UUID correlationIdentifier,
        Map<String, Object> metadata
    ) {
        return new BusinessAuditEventRequest(
            eventType,
            occurredAt,
            actor,
            subjectIdentifier.toString(),
            sourceRecordIdentifier.toString(),
            sourceEventIdentifier,
            correlationIdentifier.toString(),
            metadata
        );
    }

    private static String versionIdentifier(
        long version
    ) {
        if (version < 1L) {
            throw new InvalidBusinessAuditEventException(
                "A status-change version must be positive."
            );
        }

        return "version-" + version;
    }
}
