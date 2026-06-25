package com.samharrison.payments.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class IdentityRoleManagementService {

    private final IdentityUserRepository repository;

    private final IdentitySecurityEventRepository
        securityEventRepository;

    private final FindByIndexNameSessionRepository<
        ? extends Session
        > sessionRepository;

    private final Clock clock;

    public IdentityRoleManagementService(
        IdentityUserRepository repository,
        IdentitySecurityEventRepository
            securityEventRepository,
        FindByIndexNameSessionRepository<
            ? extends Session
            > sessionRepository,
        Clock clock
    ) {
        this.repository = repository;

        this.securityEventRepository =
            securityEventRepository;

        this.sessionRepository =
            sessionRepository;

        this.clock = clock;
    }

    @Transactional
    public IdentityRolesResponse grantRole(
        UUID actorUserId,
        UUID userId,
        IdentityRole role
    ) {
        IdentityUser user =
            findRequiredUser(userId);

        Instant changedAt =
            Instant.now(clock);

        boolean changed =
            user.grantRole(
                role,
                changedAt
            );

        if (changed) {
            securityEventRepository.save(
                IdentitySecurityEvent.roleGranted(
                    actorUserId,
                    user.id(),
                    role,
                    changedAt
                )
            );

            revokeActiveSessions(
                user.normalizedEmail()
            );
        }

        return IdentityRolesResponse.from(user);
    }

    @Transactional
    public IdentityRolesResponse revokeRole(
        UUID actorUserId,
        UUID userId,
        IdentityRole role
    ) {
        IdentityUser user =
            findRequiredUser(userId);

        Instant changedAt =
            Instant.now(clock);

        boolean changed =
            user.revokeRole(
                role,
                changedAt
            );

        if (changed) {
            securityEventRepository.save(
                IdentitySecurityEvent.roleRevoked(
                    actorUserId,
                    user.id(),
                    role,
                    changedAt
                )
            );

            revokeActiveSessions(
                user.normalizedEmail()
            );
        }

        return IdentityRolesResponse.from(user);
    }

    private IdentityUser findRequiredUser(
        UUID userId
    ) {
        return repository
            .findById(userId)
            .orElseThrow(
                () ->
                    new IdentityUserNotFoundException(
                        userId
                    )
            );
    }

    private void revokeActiveSessions(
        String normalizedEmail
    ) {
        sessionRepository
            .findByPrincipalName(
                normalizedEmail
            )
            .keySet()
            .forEach(
                sessionRepository::deleteById
            );
    }
}
