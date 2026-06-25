package com.samharrison.payments.identity.internal;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

final class IdentityUserPrincipal
    implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String normalizedEmail;
    private final HashSet<IdentityRole> roles;
    private final HashSet<SimpleGrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;

    private String passwordHash;

    private IdentityUserPrincipal(
        UUID userId,
        String email,
        String normalizedEmail,
        String passwordHash,
        Set<IdentityRole> roles,
        boolean enabled,
        boolean accountNonLocked
    ) {
        this.userId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );

        this.email = Objects.requireNonNull(
            email,
            "email must not be null"
        );

        this.normalizedEmail = Objects.requireNonNull(
            normalizedEmail,
            "normalizedEmail must not be null"
        );

        this.passwordHash = Objects.requireNonNull(
            passwordHash,
            "passwordHash must not be null"
        );

        this.roles = new HashSet<>(
            Objects.requireNonNull(
                roles,
                "roles must not be null"
            )
        );

        if (this.roles.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one role is required."
            );
        }

        this.authorities = this.roles
            .stream()
            .map(IdentityUserPrincipal::toAuthority)
            .collect(
                Collectors.toCollection(HashSet::new)
            );

        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    static IdentityUserPrincipal from(
        IdentityUser user
    ) {
        Objects.requireNonNull(
            user,
            "user must not be null"
        );

        return new IdentityUserPrincipal(
            user.id(),
            user.email(),
            user.normalizedEmail(),
            user.passwordHash(),
            user.roles(),
            user.status() != IdentityUserStatus.DISABLED,
            user.status() != IdentityUserStatus.LOCKED
        );
    }

    private static SimpleGrantedAuthority toAuthority(
        IdentityRole role
    ) {
        return new SimpleGrantedAuthority(
            "ROLE_" + role.name()
        );
    }

    UUID userId() {
        return userId;
    }

    String email() {
        return email;
    }

    Set<IdentityRole> roles() {
        return Set.copyOf(roles);
    }

    @Override
    public Collection<? extends GrantedAuthority>
    getAuthorities() {
        return Set.copyOf(authorities);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return normalizedEmail;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof IdentityUserPrincipal that)) {
            return false;
        }

        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }
}
