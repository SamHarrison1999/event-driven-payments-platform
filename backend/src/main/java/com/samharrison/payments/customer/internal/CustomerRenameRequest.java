package com.samharrison.payments.customer.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRenameRequest(
    @NotBlank(
        message = "Customer name is required."
    )
    @Size(
        max = CustomerName.MAX_LENGTH,
        message = "Customer name must not exceed "
            + CustomerName.MAX_LENGTH
            + " characters."
    )
    String fullName
) {
}