package com.samharrison.payments.reporting.internal;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class AuditEventTypeCatalog {

    private static final Map<String, Descriptor>
        DESCRIPTORS =
            Map.ofEntries(
                entry(
                    "customer.created",
                    AuditCategory.CUSTOMER,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "customer.status-changed",
                    AuditCategory.CUSTOMER,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "customer.identity-assigned",
                    AuditCategory.CUSTOMER,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "account.created",
                    AuditCategory.ACCOUNT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "account.status-changed",
                    AuditCategory.ACCOUNT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "payment.submitted",
                    AuditCategory.PAYMENT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "payment.completed",
                    AuditCategory.PAYMENT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "payment.rejected",
                    AuditCategory.PAYMENT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "payment.failed",
                    AuditCategory.PAYMENT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "settlement.import-accepted",
                    AuditCategory.SETTLEMENT,
                    AuditSource.BUSINESS_AUDIT
                ),
                entry(
                    "identity.role-granted",
                    AuditCategory.IDENTITY_SECURITY,
                    AuditSource.IDENTITY_SECURITY
                ),
                entry(
                    "identity.role-revoked",
                    AuditCategory.IDENTITY_SECURITY,
                    AuditSource.IDENTITY_SECURITY
                ),
                entry(
                    "outbox.dead-letter-replayed",
                    AuditCategory.ADMIN_RECOVERY,
                    AuditSource.OUTBOX_REPLAY
                ),
                entry(
                    "reconciliation.discrepancy-resolved",
                    AuditCategory.RECONCILIATION,
                    AuditSource.SETTLEMENT_RESOLUTION
                )
            );

    private AuditEventTypeCatalog() {
    }

    static boolean contains(String eventType) {
        return DESCRIPTORS.containsKey(eventType);
    }

    static AuditCategory category(
        String eventType
    ) {
        Descriptor descriptor =
            DESCRIPTORS.get(eventType);

        if (descriptor == null) {
            throw new IllegalArgumentException(
                "Unsupported audit event type."
            );
        }

        return descriptor.category();
    }

    static Set<String> eventTypes(
        Set<AuditCategory> categories,
        AuditSource source,
        String requestedEventType
    ) {
        return DESCRIPTORS
            .entrySet()
            .stream()
            .filter(
                entry ->
                    categories.contains(
                        entry.getValue().category()
                    )
            )
            .filter(
                entry ->
                    entry.getValue().source()
                        == source
            )
            .map(Map.Entry::getKey)
            .filter(
                eventType ->
                    requestedEventType == null
                        || requestedEventType.equals(
                            eventType
                        )
            )
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Map.Entry<String, Descriptor>
        entry(
            String eventType,
            AuditCategory category,
            AuditSource source
        ) {
        return Map.entry(
            eventType,
            new Descriptor(category, source)
        );
    }

    private record Descriptor(
        AuditCategory category,
        AuditSource source
    ) {
    }
}
