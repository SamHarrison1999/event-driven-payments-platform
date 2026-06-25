package com.samharrison.payments.identity.internal;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public final class PasswordHashingService {

    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;

    public PasswordHashingService(
        PasswordPolicy passwordPolicy,
        PasswordEncoder passwordEncoder
    ) {
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    public String hash(String rawPassword) {
        String normalizedPassword =
            passwordPolicy.validateAndNormalize(
                rawPassword
            );

        return passwordEncoder.encode(
            normalizedPassword
        );
    }

    public boolean matches(
        String rawPassword,
        String storedHash
    ) {
        if (
            rawPassword == null
                || storedHash == null
                || storedHash.isBlank()
        ) {
            return false;
        }

        String normalizedPassword =
            passwordPolicy.normalizeForVerification(
                rawPassword
            );

        try {
            return passwordEncoder.matches(
                normalizedPassword,
                storedHash
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean needsUpgrade(String storedHash) {
        if (
            storedHash == null
                || storedHash.isBlank()
        ) {
            return false;
        }

        try {
            return passwordEncoder.upgradeEncoding(
                storedHash
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
