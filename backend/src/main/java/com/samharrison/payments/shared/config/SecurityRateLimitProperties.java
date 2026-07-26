package com.samharrison.payments.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.security.rate-limit")
public record SecurityRateLimitProperties(
    boolean enabled,
    Duration window,
    int maxTrackedKeys,
    int loginRequests,
    int registrationRequests,
    int paymentRequests,
    int settlementImportRequests
) {

    public SecurityRateLimitProperties {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException(
                "rate-limit window must be positive"
            );
        }

        requirePositive(maxTrackedKeys, "maxTrackedKeys");
        requirePositive(loginRequests, "loginRequests");
        requirePositive(registrationRequests, "registrationRequests");
        requirePositive(paymentRequests, "paymentRequests");
        requirePositive(
            settlementImportRequests,
            "settlementImportRequests"
        );
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }
    }
}
