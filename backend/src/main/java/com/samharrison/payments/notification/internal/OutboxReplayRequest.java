package com.samharrison.payments.notification.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record OutboxReplayRequest(
    @NotBlank
    @Size(max = 500)
    String reason,

    @Min(0)
    long expectedVersion
) {
}
