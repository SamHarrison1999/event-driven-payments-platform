package com.samharrison.payments.account.internal;

import jakarta.validation.constraints.NotNull;

public record AccountStatusUpdateRequest(
    @NotNull(
        message = "Account status is required."
    )
    AccountStatus status
) {
}