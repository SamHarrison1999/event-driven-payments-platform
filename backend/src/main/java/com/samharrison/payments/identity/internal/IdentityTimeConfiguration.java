package com.samharrison.payments.identity.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityTimeConfiguration {

    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }
}
