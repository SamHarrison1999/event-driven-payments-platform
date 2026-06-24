package com.samharrison.payments.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdentityUserRepository
    extends JpaRepository<IdentityUser, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<IdentityUser> findByNormalizedEmail(
        String normalizedEmail
    );

    boolean existsByNormalizedEmail(
        String normalizedEmail
    );
}
