package com.samharrison.payments.notification.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationConsumerCheckpointRepository
    extends JpaRepository<
        NotificationConsumerCheckpoint,
        String
    > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT checkpoint
        FROM NotificationConsumerCheckpoint checkpoint
        WHERE checkpoint.consumerName = :consumerName
        """
    )
    Optional<NotificationConsumerCheckpoint>
        findForUpdate(
            @Param("consumerName")
            String consumerName
        );
}
