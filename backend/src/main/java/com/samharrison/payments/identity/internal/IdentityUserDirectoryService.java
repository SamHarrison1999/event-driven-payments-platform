package com.samharrison.payments.identity.internal;

import com.samharrison.payments.identity.IdentityUserDirectory;
import com.samharrison.payments.identity.IdentityUserNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityUserDirectoryService
    implements IdentityUserDirectory {

    private final IdentityUserRepository repository;

    IdentityUserDirectoryService(
        IdentityUserRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireExists(
        UUID identityUserId
    ) {
        UUID requiredIdentityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        if (
            !repository.existsById(
                requiredIdentityUserId
            )
        ) {
            throw new IdentityUserNotFoundException(
                requiredIdentityUserId
            );
        }
    }
}