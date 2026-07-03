package com.samharrison.payments.payment.internal;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueryService {

    private static final Set<String>
        PRIVILEGED_AUTHORITIES =
            Set.of(
                "ROLE_OPERATIONS",
                "ROLE_ADMIN"
            );

    private final PaymentRepository repository;

    public PaymentQueryService(
        PaymentRepository repository
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('CUSTOMER', 'OPERATIONS', 'ADMIN')"
    )
    public PaymentResponse find(
        UUID actorIdentityId,
        UUID paymentId
    ) {
        UUID requiredActorIdentityId =
            Objects.requireNonNull(
                actorIdentityId,
                "actorIdentityId must not be null"
            );

        UUID requiredPaymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        Payment payment =
            repository
                .findById(requiredPaymentId)
                .orElseThrow(
                    () ->
                        new PaymentNotFoundException(
                            requiredPaymentId
                        )
                );

        if (
            !hasPrivilegedReadAuthority()
                && !payment
                    .actorIdentityId()
                    .equals(requiredActorIdentityId)
        ) {
            throw new PaymentNotFoundException(
                requiredPaymentId
            );
        }

        return PaymentResponse.from(payment);
    }

    private static boolean
    hasPrivilegedReadAuthority() {
        Authentication authentication =
            SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (
            authentication == null
                || !authentication.isAuthenticated()
        ) {
            return false;
        }

        return authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(
                PRIVILEGED_AUTHORITIES::contains
            );
    }
}
