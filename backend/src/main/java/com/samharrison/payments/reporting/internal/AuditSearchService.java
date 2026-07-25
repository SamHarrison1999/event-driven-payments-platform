package com.samharrison.payments.reporting.internal;

import com.samharrison.payments.audit.BusinessAuditEvidence;
import com.samharrison.payments.audit.BusinessAuditEvidenceReader;
import com.samharrison.payments.audit.BusinessAuditReadCriteria;
import com.samharrison.payments.identity.IdentitySecurityAuditEvidence;
import com.samharrison.payments.identity.IdentitySecurityAuditQuery;
import com.samharrison.payments.identity.IdentitySecurityAuditReader;
import com.samharrison.payments.outbox.OutboxReplayAuditEvidence;
import com.samharrison.payments.outbox.OutboxReplayAuditQuery;
import com.samharrison.payments.outbox.OutboxReplayAuditReader;
import com.samharrison.payments.reconciliation.SettlementResolutionAuditEvidence;
import com.samharrison.payments.reconciliation.SettlementResolutionAuditQuery;
import com.samharrison.payments.reconciliation.SettlementResolutionAuditReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Service
class AuditSearchService {

    private static final String IDENTITY_USER =
        "IDENTITY_USER";

    private static final Comparator<AuditEventResponse>
        EVENT_ORDER =
            Comparator
                .comparing(
                    AuditEventResponse::occurredAt
                )
                .thenComparing(
                    AuditEventResponse::eventId
                )
                .reversed();

    private static final TypeReference<
        Map<String, Object>
    > DETAILS_TYPE =
        new TypeReference<>() {
        };

    private final BusinessAuditEvidenceReader
        businessReader;

    private final IdentitySecurityAuditReader
        identityReader;

    private final OutboxReplayAuditReader
        outboxReader;

    private final SettlementResolutionAuditReader
        resolutionReader;

    private final AuditCursorCodec cursorCodec;

    private final ObjectReader detailsReader;

    AuditSearchService(
        BusinessAuditEvidenceReader businessReader,
        IdentitySecurityAuditReader identityReader,
        OutboxReplayAuditReader outboxReader,
        SettlementResolutionAuditReader
            resolutionReader,
        AuditCursorCodec cursorCodec,
        JsonMapper jsonMapper
    ) {
        this.businessReader =
            Objects.requireNonNull(
                businessReader,
                "businessReader must not be null"
            );
        this.identityReader =
            Objects.requireNonNull(
                identityReader,
                "identityReader must not be null"
            );
        this.outboxReader =
            Objects.requireNonNull(
                outboxReader,
                "outboxReader must not be null"
            );
        this.resolutionReader =
            Objects.requireNonNull(
                resolutionReader,
                "resolutionReader must not be null"
            );
        this.cursorCodec =
            Objects.requireNonNull(
                cursorCodec,
                "cursorCodec must not be null"
            );
        detailsReader =
            Objects.requireNonNull(
                jsonMapper,
                "jsonMapper must not be null"
            ).readerFor(DETAILS_TYPE);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('OPERATIONS', "
            + "'RECONCILIATION_ANALYST', 'ADMIN')"
    )
    AuditEventPageResponse search(
        AuditSearchFilter filter
    ) {
        AuditSearchFilter requiredFilter =
            Objects.requireNonNull(
                filter,
                "filter must not be null"
            );

        Set<AuditCategory> permittedCategories =
            permittedCategories();
        Set<AuditCategory> activeCategories =
            activeCategories(
                requiredFilter,
                permittedCategories
            );

        String fingerprint =
            cursorCodec.fingerprint(
                requiredFilter,
                permittedCategories
            );
        AuditCursorCodec.Cursor cursor =
            cursorCodec.decode(
                requiredFilter.cursor(),
                fingerprint
            );

        List<AuditEventResponse> merged =
            new ArrayList<>();

        for (AuditSource source : AuditSource.values()) {
            if (
                requiredFilter.source() != null
                    && requiredFilter.source()
                        != source
            ) {
                continue;
            }

            Set<String> eventTypes =
                AuditEventTypeCatalog.eventTypes(
                    activeCategories,
                    source,
                    requiredFilter.eventType()
                );

            if (eventTypes.isEmpty()) {
                continue;
            }

            SourceReadValues values =
                readValues(
                    requiredFilter,
                    cursor,
                    eventTypes
                );

            readSource(
                source,
                values,
                merged
            );
        }

        merged.sort(EVENT_ORDER);

        boolean hasMore =
            merged.size() > requiredFilter.limit();
        List<AuditEventResponse> content =
            hasMore
                ? List.copyOf(
                    merged.subList(
                        0,
                        requiredFilter.limit()
                    )
                )
                : List.copyOf(merged);

        String nextCursor = null;

        if (hasMore) {
            AuditEventResponse last =
                content.getLast();

            nextCursor =
                cursorCodec.encode(
                    last.occurredAt(),
                    last.eventId(),
                    fingerprint
                );
        }

        return new AuditEventPageResponse(
            content,
            nextCursor
        );
    }

    private static SourceReadValues readValues(
        AuditSearchFilter filter,
        AuditCursorCodec.Cursor cursor,
        Set<String> eventTypes
    ) {
        return new SourceReadValues(
            filter.from(),
            filter.to(),
            eventTypes,
            filter.actorIdentityUserId(),
            filter.subjectType(),
            filter.subjectIdentifier(),
            filter.correlationIdentifier(),
            cursor == null
                ? null
                : cursor.occurredAt(),
            cursor == null
                ? null
                : cursor.eventId(),
            filter.limit() + 1
        );
    }

    private void readSource(
        AuditSource source,
        SourceReadValues values,
        List<AuditEventResponse> destination
    ) {
        switch (source) {
            case BUSINESS_AUDIT ->
                businessReader
                    .read(values.businessQuery())
                    .stream()
                    .map(this::mapBusinessEvidence)
                    .forEach(destination::add);
            case IDENTITY_SECURITY ->
                identityReader
                    .read(values.identityQuery())
                    .stream()
                    .map(
                        AuditSearchService
                            ::mapIdentityEvidence
                    )
                    .forEach(destination::add);
            case OUTBOX_REPLAY ->
                outboxReader
                    .read(values.outboxQuery())
                    .stream()
                    .map(
                        AuditSearchService
                            ::mapOutboxEvidence
                    )
                    .forEach(destination::add);
            case SETTLEMENT_RESOLUTION ->
                resolutionReader
                    .read(values.resolutionQuery())
                    .stream()
                    .map(
                        AuditSearchService
                            ::mapResolutionEvidence
                    )
                    .forEach(destination::add);
        }
    }

    private AuditEventResponse mapBusinessEvidence(
        BusinessAuditEvidence evidence
    ) {
        return new AuditEventResponse(
            qualifiedId(
                AuditSource.BUSINESS_AUDIT,
                evidence.eventId()
            ),
            AuditSource.BUSINESS_AUDIT,
            AuditEventTypeCatalog.category(
                evidence.eventType()
            ),
            evidence.eventType(),
            evidence.schemaVersion(),
            evidence.occurredAt(),
            evidence.actorKind().name(),
            evidence.actorIdentityUserId(),
            evidence.subjectType(),
            evidence.subjectIdentifier(),
            evidence.correlationIdentifier(),
            readDetails(evidence.metadata())
        );
    }

    private Map<String, Object> readDetails(
        String metadata
    ) {
        try {
            Map<String, Object> details =
                detailsReader.readValue(metadata);

            return Map.copyOf(details);
        }
        catch (JacksonException failure) {
            throw new IllegalStateException(
                "Stored business-audit metadata "
                    + "is invalid.",
                failure
            );
        }
    }

    private static AuditEventResponse
        mapIdentityEvidence(
            IdentitySecurityAuditEvidence evidence
        ) {
        return new AuditEventResponse(
            qualifiedId(
                AuditSource.IDENTITY_SECURITY,
                evidence.eventId()
            ),
            AuditSource.IDENTITY_SECURITY,
            AuditCategory.IDENTITY_SECURITY,
            evidence.eventType(),
            1,
            evidence.occurredAt(),
            IDENTITY_USER,
            evidence.actorIdentityUserId(),
            "identity_user",
            evidence
                .subjectIdentityUserId()
                .toString(),
            null,
            Map.of("role", evidence.role())
        );
    }

    private static AuditEventResponse
        mapOutboxEvidence(
            OutboxReplayAuditEvidence evidence
        ) {
        return new AuditEventResponse(
            qualifiedId(
                AuditSource.OUTBOX_REPLAY,
                evidence.auditEventId()
            ),
            AuditSource.OUTBOX_REPLAY,
            AuditCategory.ADMIN_RECOVERY,
            "outbox.dead-letter-replayed",
            1,
            evidence.replayedAt(),
            IDENTITY_USER,
            evidence.actorIdentityUserId(),
            "outbox_event",
            evidence.outboxEventId().toString(),
            null,
            Map.of(
                "eventVersionBefore",
                evidence.eventVersionBefore()
            )
        );
    }

    private static AuditEventResponse
        mapResolutionEvidence(
            SettlementResolutionAuditEvidence evidence
        ) {
        return new AuditEventResponse(
            qualifiedId(
                AuditSource.SETTLEMENT_RESOLUTION,
                evidence.resolutionId()
            ),
            AuditSource.SETTLEMENT_RESOLUTION,
            AuditCategory.RECONCILIATION,
            "reconciliation.discrepancy-resolved",
            1,
            evidence.decidedAt(),
            IDENTITY_USER,
            evidence.actorIdentityUserId(),
            "settlement_discrepancy",
            evidence.discrepancyId().toString(),
            null,
            Map.of(
                "decision",
                evidence.decision(),
                "discrepancyVersion",
                evidence.discrepancyVersion()
            )
        );
    }

    private static String qualifiedId(
        AuditSource source,
        Object identifier
    ) {
        return source.name()
            + ":"
            + identifier;
    }

    private static Set<AuditCategory>
        activeCategories(
            AuditSearchFilter filter,
            Set<AuditCategory> permittedCategories
        ) {
        if (filter.category() == null) {
            return permittedCategories;
        }

        if (
            permittedCategories.contains(
                filter.category()
            )
        ) {
            return Set.of(filter.category());
        }

        return Set.of();
    }

    private static Set<AuditCategory>
        permittedCategories() {
        Authentication authentication =
            SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (
            authentication == null
                || !authentication.isAuthenticated()
        ) {
            return Set.of();
        }

        Set<String> authorities =
            authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(
                    java.util.stream.Collectors
                        .toUnmodifiableSet()
                );

        if (authorities.contains("ROLE_ADMIN")) {
            return Set.copyOf(
                EnumSet.allOf(
                    AuditCategory.class
                )
            );
        }

        EnumSet<AuditCategory> permitted =
            EnumSet.noneOf(AuditCategory.class);

        if (
            authorities.contains(
                "ROLE_OPERATIONS"
            )
        ) {
            permitted.add(AuditCategory.CUSTOMER);
            permitted.add(AuditCategory.ACCOUNT);
            permitted.add(AuditCategory.PAYMENT);
        }

        if (
            authorities.contains(
                "ROLE_RECONCILIATION_ANALYST"
            )
        ) {
            permitted.add(AuditCategory.SETTLEMENT);
            permitted.add(
                AuditCategory.RECONCILIATION
            );
        }

        return Set.copyOf(permitted);
    }

    private record SourceReadValues(
        Instant from,
        Instant to,
        Set<String> eventTypes,
        java.util.UUID actorIdentityUserId,
        String subjectType,
        String subjectIdentifier,
        String correlationIdentifier,
        Instant cursorOccurredAt,
        String cursorEventId,
        int limit
    ) {

        private BusinessAuditReadCriteria
            businessQuery() {
            return new BusinessAuditReadCriteria(
                from,
                to,
                eventTypes,
                actorIdentityUserId,
                subjectType,
                subjectIdentifier,
                correlationIdentifier,
                cursorOccurredAt,
                cursorEventId,
                limit
            );
        }

        private IdentitySecurityAuditQuery
            identityQuery() {
            return new IdentitySecurityAuditQuery(
                from,
                to,
                eventTypes,
                actorIdentityUserId,
                subjectType,
                subjectIdentifier,
                correlationIdentifier,
                cursorOccurredAt,
                cursorEventId,
                limit
            );
        }

        private OutboxReplayAuditQuery
            outboxQuery() {
            return new OutboxReplayAuditQuery(
                from,
                to,
                eventTypes,
                actorIdentityUserId,
                subjectType,
                subjectIdentifier,
                correlationIdentifier,
                cursorOccurredAt,
                cursorEventId,
                limit
            );
        }

        private SettlementResolutionAuditQuery
            resolutionQuery() {
            return new SettlementResolutionAuditQuery(
                from,
                to,
                eventTypes,
                actorIdentityUserId,
                subjectType,
                subjectIdentifier,
                correlationIdentifier,
                cursorOccurredAt,
                cursorEventId,
                limit
            );
        }
    }
}
