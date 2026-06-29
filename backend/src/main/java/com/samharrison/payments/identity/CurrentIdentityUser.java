package com.samharrison.payments.identity;

import java.util.UUID;

public interface CurrentIdentityUser {

    UUID requireUserId();
}