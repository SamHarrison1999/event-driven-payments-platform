package com.samharrison.payments.notification.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository
    extends JpaRepository<Notification, UUID> {

    boolean existsBySourceEventId(UUID sourceEventId);

    @Query(
        value = """
            SELECT *
            FROM notification
            WHERE (
                status = 'PENDING'
                AND next_attempt_at <= :claimTime
            )
            OR (
                status = 'DELIVERING'
                AND delivery_lease_expires_at
                    <= :claimTime
            )
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """,
        nativeQuery = true
    )
    List<Notification> findClaimable(
        @Param("claimTime") Instant claimTime,
        @Param("batchSize") int batchSize
    );

    @Query(
        value = """
            SELECT *
            FROM notification
            WHERE recipient_identity_user_id
                = :recipientIdentityUserId
            ORDER BY created_at DESC, id DESC
            LIMIT :batchSize
            """,
        nativeQuery = true
    )
    List<Notification> findOwned(
        @Param("recipientIdentityUserId")
        UUID recipientIdentityUserId,
        @Param("batchSize") int batchSize
    );
}
