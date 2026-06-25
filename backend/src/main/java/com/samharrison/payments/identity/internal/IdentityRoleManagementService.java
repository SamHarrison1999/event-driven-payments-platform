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

    private final FindByIndexNameSessionRepository<
        ? extends Session
        > sessionRepository;

    private final Clock clock;

    public IdentityRoleManagementService(
        IdentityUserRepository repository,
        FindByIndexNameSessionRepository<
            ? extends Session
            > sessionRepository,
        Clock clock
    ) {
        this.repository = repository;
        this.sessionRepository =
            sessionRepository;
        this.clock = clock;
    }

    @Transactional
    public IdentityRolesResponse grantRole(
        UUID userId,
        IdentityRole role
    ) {
        IdentityUser user =
            findRequiredUser(userId);

        boolean changed =
            user.grantRole(
                role,
                Instant.now(clock)
            );

        if (changed) {
            revokeActiveSessions(
                user.normalizedEmail()
            );
        }

        return IdentityRolesResponse.from(user);
    }

    @Transactional
    public IdentityRolesResponse revokeRole(
        UUID userId,
        IdentityRole role
    ) {
        IdentityUser user =
            findRequiredUser(userId);

        boolean changed =
            user.revokeRole(
                role,
                Instant.now(clock)
            );

        if (changed) {
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
