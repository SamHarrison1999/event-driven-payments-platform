package com.samharrison.payments.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdentityUserRepository
    extends JpaRepository<IdentityUser, UUID> {

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<IdentityUser> findById(UUID id);

    @EntityGraph(attributePaths = "roles")
    Optional<IdentityUser> findByNormalizedEmail(
        String normalizedEmail
    );

    boolean existsByNormalizedEmail(
        String normalizedEmail
    );
}
