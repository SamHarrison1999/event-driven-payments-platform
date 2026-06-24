package com.samharrison.payments.shared.api;

public record SystemInfoResponse(
    String name,
    String description,
    String version,
    boolean educational,
    boolean realMoneyProcessing
) {
}
