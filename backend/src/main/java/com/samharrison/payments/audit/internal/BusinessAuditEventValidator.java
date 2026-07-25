package com.samharrison.payments.audit.internal;

import com.samharrison.payments.audit.BusinessAuditEventRequest;
import com.samharrison.payments.audit.BusinessAuditEventType;
import com.samharrison.payments.audit.InvalidBusinessAuditEventException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class BusinessAuditEventValidator {

    private static final Set<String> CUSTOMER_STATUSES =
        Set.of(
            "ACTIVE",
            "SUSPENDED",
            "CLOSED"
        );

    private static final Set<String> ACCOUNT_STATUSES =
        Set.of(
            "ACTIVE",
            "FROZEN",
            "CLOSED"
        );

    private static final Set<String> REJECTION_CODES =
        Set.of(
            "PAYMENT_SOURCE_NOT_OWNED",
            "PAYMENT_SOURCE_NOT_FOUND",
            "PAYMENT_DESTINATION_NOT_FOUND",
            "PAYMENT_SOURCE_NOT_ACTIVE",
            "PAYMENT_DESTINATION_NOT_ACTIVE",
            "PAYMENT_CURRENCY_MISMATCH",
            "PAYMENT_INSUFFICIENT_FUNDS"
        );

    private static final Set<String> FAILURE_CODES =
        Set.of(
            "PAYMENT_PROCESSING_FAILED",
            "PAYMENT_CONCURRENT_MODIFICATION"
        );

    void validate(
        BusinessAuditEventRequest request
    ) {
        BusinessAuditEventRequest requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        Map<String, Object> metadata =
            requiredRequest.metadata();

        if (
            requiredRequest.eventType()
                != BusinessAuditEventType
                    .IDENTITY_CUSTOMER_ASSIGNED
                && !requiredRequest
                    .subjectIdentifier()
                    .equals(
                        requiredRequest
                            .sourceRecordIdentifier()
                    )
        ) {
            throw new InvalidBusinessAuditEventException(
                "The source record must identify the "
                    + "audited subject."
            );
        }

        switch (requiredRequest.eventType()) {
            case CUSTOMER_CREATED ->
                validateCreatedStatus(
                    metadata,
                    CUSTOMER_STATUSES
                );
            case CUSTOMER_STATUS_CHANGED ->
                validateStatusChange(
                    metadata,
                    CUSTOMER_STATUSES
                );
            case ACCOUNT_CREATED ->
                validateAccountCreated(metadata);
            case ACCOUNT_STATUS_CHANGED ->
                validateStatusChange(
                    metadata,
                    ACCOUNT_STATUSES
                );
            case IDENTITY_CUSTOMER_ASSIGNED ->
                validateCustomerAssignment(
                    requiredRequest,
                    metadata
                );
            case PAYMENT_SUBMITTED ->
                validatePaymentSubmitted(metadata);
            case PAYMENT_COMPLETED ->
                validatePaymentCompleted(metadata);
            case PAYMENT_REJECTED ->
                validateCode(
                    metadata,
                    "reasonCode",
                    REJECTION_CODES
                );
            case PAYMENT_FAILED ->
                validateCode(
                    metadata,
                    "failureCode",
                    FAILURE_CODES
                );
            case SETTLEMENT_IMPORT_ACCEPTED ->
                validateSettlementImport(metadata);
        }
    }

    private static void validateCreatedStatus(
        Map<String, Object> metadata,
        Set<String> allowedStatuses
    ) {
        requireExactKeys(
            metadata,
            Set.of("status")
        );

        String status =
            requireAllowedString(
                metadata,
                "status",
                allowedStatuses
            );

        if (!"ACTIVE".equals(status)) {
            throw invalidMetadata();
        }
    }

    private static void validateStatusChange(
        Map<String, Object> metadata,
        Set<String> allowedStatuses
    ) {
        requireExactKeys(
            metadata,
            Set.of(
                "previousStatus",
                "newStatus"
            )
        );

        String previous =
            requireAllowedString(
                metadata,
                "previousStatus",
                allowedStatuses
            );

        String next =
            requireAllowedString(
                metadata,
                "newStatus",
                allowedStatuses
            );

        if (previous.equals(next)) {
            throw invalidMetadata();
        }
    }

    private static void validateAccountCreated(
        Map<String, Object> metadata
    ) {
        requireExactKeys(
            metadata,
            Set.of(
                "customerId",
                "currency",
                "status"
            )
        );

        requireString(metadata, "customerId");

        if (
            !"GBP".equals(
                requireString(metadata, "currency")
            )
                || !"ACTIVE".equals(
                    requireString(metadata, "status")
                )
        ) {
            throw invalidMetadata();
        }
    }

    private static void validateCustomerAssignment(
        BusinessAuditEventRequest request,
        Map<String, Object> metadata
    ) {
        requireExactKeys(
            metadata,
            Set.of("customerId")
        );

        if (
            !request.subjectIdentifier().equals(
                requireString(
                    metadata,
                    "customerId"
                )
            )
        ) {
            throw invalidMetadata();
        }
    }

    private static void validatePaymentSubmitted(
        Map<String, Object> metadata
    ) {
        requireExactKeys(
            metadata,
            Set.of(
                "amountMinor",
                "currency",
                "destinationAccountId",
                "sourceAccountId"
            )
        );

        requirePositiveLong(
            metadata,
            "amountMinor"
        );
        requireString(
            metadata,
            "destinationAccountId"
        );
        requireString(
            metadata,
            "sourceAccountId"
        );

        if (
            !"GBP".equals(
                requireString(metadata, "currency")
            )
        ) {
            throw invalidMetadata();
        }
    }

    private static void validatePaymentCompleted(
        Map<String, Object> metadata
    ) {
        requireExactKeys(
            metadata,
            Set.of(
                "amountMinor",
                "currency"
            )
        );

        requirePositiveLong(
            metadata,
            "amountMinor"
        );

        if (
            !"GBP".equals(
                requireString(metadata, "currency")
            )
        ) {
            throw invalidMetadata();
        }
    }

    private static void validateCode(
        Map<String, Object> metadata,
        String key,
        Set<String> allowedCodes
    ) {
        requireExactKeys(
            metadata,
            Set.of(key)
        );
        requireAllowedString(
            metadata,
            key,
            allowedCodes
        );
    }

    private static void validateSettlementImport(
        Map<String, Object> metadata
    ) {
        requireExactKeys(
            metadata,
            Set.of(
                "discrepancyCount",
                "matchedCount",
                "rowCount"
            )
        );

        long rowCount =
            requireNonNegativeLong(
                metadata,
                "rowCount"
            );
        long matchedCount =
            requireNonNegativeLong(
                metadata,
                "matchedCount"
            );
        long discrepancyCount =
            requireNonNegativeLong(
                metadata,
                "discrepancyCount"
            );

        if (
            rowCount == 0L
                || matchedCount
                    > Long.MAX_VALUE
                        - discrepancyCount
                || rowCount
                    != matchedCount
                        + discrepancyCount
        ) {
            throw invalidMetadata();
        }
    }

    private static void requireExactKeys(
        Map<String, Object> metadata,
        Set<String> requiredKeys
    ) {
        if (!metadata.keySet().equals(requiredKeys)) {
            throw invalidMetadata();
        }
    }

    private static String requireAllowedString(
        Map<String, Object> metadata,
        String key,
        Set<String> allowedValues
    ) {
        String value =
            requireString(metadata, key);

        if (!allowedValues.contains(value)) {
            throw invalidMetadata();
        }

        return value;
    }

    private static String requireString(
        Map<String, Object> metadata,
        String key
    ) {
        Object value = metadata.get(key);

        if (value instanceof String text) {
            return text;
        }

        throw invalidMetadata();
    }

    private static long requirePositiveLong(
        Map<String, Object> metadata,
        String key
    ) {
        long value =
            requireNonNegativeLong(
                metadata,
                key
            );

        if (value == 0L) {
            throw invalidMetadata();
        }

        return value;
    }

    private static long requireNonNegativeLong(
        Map<String, Object> metadata,
        String key
    ) {
        Object value = metadata.get(key);

        if (
            value instanceof Long number
                && number >= 0L
        ) {
            return number;
        }

        throw invalidMetadata();
    }

    private static
        InvalidBusinessAuditEventException
        invalidMetadata() {
        return new InvalidBusinessAuditEventException(
            "metadata does not match the versioned "
                + "event schema."
        );
    }
}
