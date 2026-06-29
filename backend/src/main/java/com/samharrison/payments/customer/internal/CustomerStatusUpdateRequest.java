package com.samharrison.payments.customer.internal;

import jakarta.validation.constraints.NotNull;

public record CustomerStatusUpdateRequest(
    @NotNull(
        message = "Customer status is required."
    )
    CustomerStatus status
) {
}