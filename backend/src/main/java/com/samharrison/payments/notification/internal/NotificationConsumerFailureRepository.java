package com.samharrison.payments.notification.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationConsumerFailureRepository
    extends JpaRepository<
        NotificationConsumerFailure,
        UUID
    > {

    boolean existsBySourceEventId(UUID sourceEventId);
}
