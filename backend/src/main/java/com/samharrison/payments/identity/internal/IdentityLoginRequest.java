package com.samharrison.payments.identity.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IdentityLoginRequest(
    @NotBlank(
        message = "Email address is required."
    )
    @Size(
        max = EmailAddress.MAX_LENGTH,
        message = "Email address must not exceed "
            + EmailAddress.MAX_LENGTH
            + " characters."
    )
    @Email(
        message = "Email address must be valid."
    )
    String email,

    @NotNull(
        message = "Password is required."
    )
    @Size(
        max = PasswordPolicy.MAXIMUM_LENGTH,
        message = "Password must not exceed "
            + PasswordPolicy.MAXIMUM_LENGTH
            + " characters."
    )
    @JsonProperty(
        access = JsonProperty.Access.WRITE_ONLY
    )
    String password
) {

    @Override
    public String toString() {
        return "IdentityLoginRequest["
            + "email=[REDACTED], "
            + "password=[REDACTED]"
            + "]";
    }
}
