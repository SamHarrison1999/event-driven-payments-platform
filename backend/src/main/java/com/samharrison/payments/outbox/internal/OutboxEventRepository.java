package com.samharrison.payments.outbox.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxEventRepository
    extends JpaRepository<OutboxEvent, UUID> {

    @Query(
        value = """
            SELECT *
            FROM outbox_event
            WHERE (
                status = 'PENDING'
                AND next_attempt_at <= :claimTime
            )
            OR (
                status = 'PUBLISHING'
                AND publication_lease_expires_at
                    <= :claimTime
            )
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """,
        nativeQuery = true
    )
    List<OutboxEvent> findClaimable(
        @Param("claimTime") Instant claimTime,
        @Param("batchSize") int batchSize
    );
}
