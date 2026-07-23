package com.samharrison.payments.outbox.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxReplayAuditRepository
    extends JpaRepository<OutboxReplayAudit, UUID> {
}
