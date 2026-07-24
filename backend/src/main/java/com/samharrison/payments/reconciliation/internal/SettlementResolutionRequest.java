package com.samharrison.payments.reconciliation.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SettlementResolutionRequest(
    @NotNull
    SettlementResolutionDecision decision,
    @NotBlank
    @Size(max = SettlementResolution.MAX_REASON_LENGTH)
    String reason
) {
}
