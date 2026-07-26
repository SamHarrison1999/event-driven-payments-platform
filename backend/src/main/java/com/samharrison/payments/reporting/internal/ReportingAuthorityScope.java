package com.samharrison.payments.reporting.internal;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

record ReportingAuthorityScope(
    boolean administrator,
    boolean operations,
    boolean reconciliationAnalyst
) {

    static ReportingAuthorityScope current() {
        Authentication authentication =
            SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (
            authentication == null
                || !authentication.isAuthenticated()
        ) {
            return new ReportingAuthorityScope(
                false,
                false,
                false
            );
        }

        Set<String> authorities =
            authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(
                    java.util.stream.Collectors
                        .toUnmodifiableSet()
                );

        return new ReportingAuthorityScope(
            authorities.contains("ROLE_ADMIN"),
            authorities.contains(
                "ROLE_OPERATIONS"
            ),
            authorities.contains(
                "ROLE_RECONCILIATION_ANALYST"
            )
        );
    }

    boolean mayReadPayments() {
        return administrator || operations;
    }

    boolean mayReadReconciliation() {
        return administrator
            || reconciliationAnalyst;
    }
}
