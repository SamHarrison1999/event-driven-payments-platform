package com.samharrison.payments.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class IdentityAuthenticationConfiguration {

    @Bean
    IdentityLockoutPolicy identityLockoutPolicy() {
        return IdentityLockoutPolicy.standard();
    }

    @Bean
    AuthenticationManager identityAuthenticationManager(
        IdentityUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider authenticationProvider =
            new DaoAuthenticationProvider(userDetailsService);

        authenticationProvider.setPasswordEncoder(
            passwordEncoder
        );

        return new ProviderManager(
            authenticationProvider
        );
    }
}
