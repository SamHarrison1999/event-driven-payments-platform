package com.samharrison.payments.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.audit.BusinessAuditActorKind;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class AuditSearchServiceTest {

    private static final Instant FROM =
        Instant.parse(
            "2026-07-25T00:00:00Z"
        );

    private static final Instant TO =
        Instant.parse(
            "2026-07-26T00:00:00Z"
        );

    private final StubBusinessReader
        businessReader =
            new StubBusinessReader();

    private final StubIdentityReader
        identityReader =
            new StubIdentityReader();

    private final StubOutboxReader
        outboxReader =
            new StubOutboxReader();

    private final StubResolutionReader
        resolutionReader =
            new StubResolutionReader();

    private final AuditSearchService service =
        new AuditSearchService(
            businessReader,
            identityReader,
            outboxReader,
            resolutionReader,
            new AuditCursorCodec(),
            JsonMapper.builder().build()
        );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void operationsScopeIsAppliedBeforeReading() {
        authenticate("ROLE_OPERATIONS");

        businessReader.evidence =
            List.of(
                customerEvidence(
                    Instant.parse(
                        "2026-07-25T12:00:00Z"
                    )
                )
            );

        AuditEventPageResponse page =
            service.search(filter(10));

        assertThat(page.events())
            .extracting(
                AuditEventResponse::category
            )
            .containsExactly(
                AuditCategory.CUSTOMER
            );

        assertThat(
            businessReader
                .criteria
                .eventTypes()
        )
            .contains(
                "customer.created",
                "account.created",
                "payment.submitted"
            )
            .doesNotContain(
                "settlement.import-accepted"
            );

        assertThat(identityReader.calls).isZero();
        assertThat(outboxReader.calls).isZero();
        assertThat(resolutionReader.calls).isZero();
    }

    @Test
    void adminReceivesMergedSafeSourceEvidence() {
        authenticate("ROLE_ADMIN");

        businessReader.evidence =
            List.of(
                customerEvidence(
                    Instant.parse(
                        "2026-07-25T15:00:00Z"
                    )
                )
            );
        identityReader.evidence =
            List.of(
                new IdentitySecurityAuditEvidence(
                    UUID.randomUUID(),
                    "identity.role-granted",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ADMIN",
                    Instant.parse(
                        "2026-07-25T14:00:00Z"
                    )
                )
            );
        outboxReader.evidence =
            List.of(
                new OutboxReplayAuditEvidence(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    2,
                    Instant.parse(
                        "2026-07-25T13:00:00Z"
                    )
                )
            );
        resolutionReader.evidence =
            List.of(
                new SettlementResolutionAuditEvidence(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ACCEPTED",
                    3,
                    Instant.parse(
                        "2026-07-25T12:00:00Z"
                    )
                )
            );

        AuditEventPageResponse page =
            service.search(filter(10));

        assertThat(page.events())
            .extracting(AuditEventResponse::source)
            .containsExactly(
                AuditSource.BUSINESS_AUDIT,
                AuditSource.IDENTITY_SECURITY,
                AuditSource.OUTBOX_REPLAY,
                AuditSource.SETTLEMENT_RESOLUTION
            );
        assertThat(page.events().get(2).details())
            .containsOnlyKeys(
                "eventVersionBefore"
            );
        assertThat(page.events().get(3).details())
            .containsOnlyKeys(
                "decision",
                "discrepancyVersion"
            );
        assertThat(page.nextCursor()).isNull();
    }

    private static BusinessAuditEvidence
        customerEvidence(Instant occurredAt) {
        UUID customerId = UUID.randomUUID();

        return new BusinessAuditEvidence(
            UUID.randomUUID(),
            "customer.created",
            1,
            occurredAt,
            BusinessAuditActorKind.SYSTEM,
            null,
            "customer",
            customerId.toString(),
            "correlation-" + customerId,
            "{\"status\":\"ACTIVE\"}"
        );
    }

    private static AuditSearchFilter filter(
        int limit
    ) {
        return new AuditSearchFilter(
            FROM,
            TO,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            limit
        );
    }

    private static void authenticate(
        String authority
    ) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken(
                    "test-user",
                    "test-password",
                    authority
                )
            );
    }

    private static final class StubBusinessReader
        implements BusinessAuditEvidenceReader {

        private List<BusinessAuditEvidence> evidence =
            List.of();

        private BusinessAuditReadCriteria criteria;

        @Override
        public List<BusinessAuditEvidence> read(
            BusinessAuditReadCriteria criteria
        ) {
            this.criteria = criteria;
            return evidence;
        }
    }

    private static final class StubIdentityReader
        implements IdentitySecurityAuditReader {

        private List<IdentitySecurityAuditEvidence>
            evidence =
                List.of();

        private int calls;

        @Override
        public List<IdentitySecurityAuditEvidence>
            read(
                IdentitySecurityAuditQuery query
            ) {
            calls++;
            return evidence;
        }
    }

    private static final class StubOutboxReader
        implements OutboxReplayAuditReader {

        private List<OutboxReplayAuditEvidence>
            evidence =
                List.of();

        private int calls;

        @Override
        public List<OutboxReplayAuditEvidence> read(
            OutboxReplayAuditQuery query
        ) {
            calls++;
            return evidence;
        }
    }

    private static final class StubResolutionReader
        implements SettlementResolutionAuditReader {

        private List<
            SettlementResolutionAuditEvidence
        > evidence =
            List.of();

        private int calls;

        @Override
        public List<
            SettlementResolutionAuditEvidence
        > read(
            SettlementResolutionAuditQuery query
        ) {
            calls++;
            return evidence;
        }
    }
}
