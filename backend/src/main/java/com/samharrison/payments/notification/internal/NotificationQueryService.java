package com.samharrison.payments.notification.internal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CUSTOMER')")
public class NotificationQueryService {

    private static final int MAX_BATCH_SIZE = 100;

    private final NotificationRepository repository;

    public NotificationQueryService(
        NotificationRepository repository
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findOwned(
        UUID recipientIdentityUserId,
        int requestedBatchSize
    ) {
        UUID requiredRecipient =
            Objects.requireNonNull(
                recipientIdentityUserId,
                "recipientIdentityUserId must not be null"
            );

        if (
            requestedBatchSize < 1
                || requestedBatchSize > MAX_BATCH_SIZE
        ) {
            throw new IllegalArgumentException(
                "requestedBatchSize must be between 1 and "
                    + MAX_BATCH_SIZE
            );
        }

        return repository
            .findOwned(
                requiredRecipient,
                requestedBatchSize
            )
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }
}
