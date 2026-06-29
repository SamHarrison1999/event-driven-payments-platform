package com.samharrison.payments.account.internal;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AccountCreateRequest(
    @NotNull(
        message = "Customer id is required."
    )
    UUID customerId
) {
}