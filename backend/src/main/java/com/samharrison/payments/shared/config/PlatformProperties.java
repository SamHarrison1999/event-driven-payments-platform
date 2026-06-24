package com.samharrison.payments.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
    @NotBlank String name,
    @NotBlank String description,
    @NotBlank String version,
    boolean educational,
    boolean realMoneyProcessing
) {
}
