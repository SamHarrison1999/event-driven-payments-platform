package com.samharrison.payments.identity.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdentitySecurityEventRepository
    extends JpaRepository<
    IdentitySecurityEvent,
    UUID
    > {

    List<IdentitySecurityEvent>
    findAllBySubjectUserIdOrderByOccurredAtAsc(
        UUID subjectUserId
    );
}
