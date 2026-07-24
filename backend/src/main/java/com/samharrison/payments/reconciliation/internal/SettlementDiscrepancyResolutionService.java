package com.samharrison.payments.reconciliation.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementDiscrepancyResolutionService {

    private final SettlementDiscrepancyRepository
        discrepancyRepository;

    private final SettlementResolutionRepository
        resolutionRepository;

    private final Clock clock;

    SettlementDiscrepancyResolutionService(
        SettlementDiscrepancyRepository
            discrepancyRepository,
        SettlementResolutionRepository
            resolutionRepository,
        Clock clock
    ) {
        this.discrepancyRepository =
            Objects.requireNonNull(
                discrepancyRepository,
                "discrepancyRepository must not be null"
            );
        this.resolutionRepository =
            Objects.requireNonNull(
                resolutionRepository,
                "resolutionRepository must not be null"
            );
        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', 'ADMIN')"
    )
    void resolve(
        UUID discrepancyId,
        long expectedVersion,
        UUID actorIdentityUserId,
        SettlementResolutionDecision decision,
        String reason
    ) {
        UUID requiredId =
            Objects.requireNonNull(
                discrepancyId,
                "discrepancyId must not be null"
            );

        SettlementDiscrepancy discrepancy =
            discrepancyRepository
                .findForUpdate(requiredId)
                .orElseThrow(
                    () ->
                        new
                        SettlementDiscrepancyNotFoundException(
                            requiredId
                        )
                );

        if (discrepancy.version() != expectedVersion) {
            throw new
                SettlementDiscrepancyVersionConflictException(
                    discrepancy.id(),
                    expectedVersion,
                    discrepancy.version()
                );
        }

        if (
            discrepancy.status()
                != SettlementDiscrepancyStatus.OPEN
        ) {
            throw new
                SettlementDiscrepancyLifecycleException(
                    discrepancy.id()
                );
        }

        Instant decidedAt = clock.instant();
        SettlementResolution resolution =
            SettlementResolution.decide(
                discrepancy,
                actorIdentityUserId,
                decision,
                reason,
                decidedAt
            );

        resolutionRepository.saveAndFlush(resolution);

        discrepancy.resolve();
        discrepancyRepository.saveAndFlush(discrepancy);
    }
}
