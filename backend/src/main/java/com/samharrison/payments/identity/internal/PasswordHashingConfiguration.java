package com.samharrison.payments.identity.internal;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordHashingConfiguration {

    public static final String CURRENT_ENCODING_ID =
        "pbkdf2@SpringSecurity_v5_8";

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders =
            Map.of(
                CURRENT_ENCODING_ID,
                Pbkdf2PasswordEncoder
                    .defaultsForSpringSecurity_v5_8()
            );

        return new DelegatingPasswordEncoder(
            CURRENT_ENCODING_ID,
            encoders
        );
    }
}
