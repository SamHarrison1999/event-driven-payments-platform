package com.samharrison.payments.identity;

import java.util.UUID;

public interface IdentityUserDirectory {

    void requireExists(
        UUID identityUserId
    );
}