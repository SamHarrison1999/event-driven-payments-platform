package com.samharrison.payments.notification.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository
    extends JpaRepository<Notification, UUID> {

    boolean existsBySourceEventId(UUID sourceEventId);
}
