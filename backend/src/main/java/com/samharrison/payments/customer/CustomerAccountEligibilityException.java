package com.samharrison.payments.customer;

import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

public final class CustomerAccountEligibilityException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID customerId;

    private final Reason reason;

    public CustomerAccountEligibilityException(
        UUID customerId,
        Reason reason
    ) {
        super(message(customerId, reason));

        this.customerId =
            Objects.requireNonNull(
                customerId,
                "customerId must not be null"
            );

        this.reason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );
    }

    public UUID customerId() {
        return customerId;
    }

    public Reason reason() {
        return reason;
    }

    private static String message(
        UUID customerId,
        Reason reason
    ) {
        Objects.requireNonNull(
            customerId,
            "customerId must not be null"
        );

        Objects.requireNonNull(
            reason,
            "reason must not be null"
        );

        return switch (reason) {
            case NOT_FOUND ->
                "Customer "
                    + customerId
                    + " was not found.";
            case NOT_ACTIVE ->
                "Customer "
                    + customerId
                    + " is not active and cannot "
                    + "receive a new account.";
        };
    }

    public enum Reason {
        NOT_FOUND,
        NOT_ACTIVE
    }
}