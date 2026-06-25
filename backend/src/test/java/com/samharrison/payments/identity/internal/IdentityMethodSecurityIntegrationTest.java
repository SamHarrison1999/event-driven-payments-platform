package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
        IdentityMethodSecurityConfiguration.class,
        IdentityMethodSecurityIntegrationTest
            .TestBeans.class
    }
)
class IdentityMethodSecurityIntegrationTest {

    @jakarta.annotation.Resource
    private SecuredOperationsService
        securedOperationsService;

    @Test
    @WithMockUser(roles = "OPERATIONS")
    void allowsOperationsUsers() {
        assertThat(
            securedOperationsService
                .inspectFailedPayment()
        )
            .isEqualTo(
                "failed-payment-details"
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void allowsAdministrators() {
        assertThat(
            securedOperationsService
                .inspectFailedPayment()
        )
            .isEqualTo(
                "failed-payment-details"
            );
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void rejectsCustomerUsers() {
        assertThatThrownBy(
            () ->
                securedOperationsService
                    .inspectFailedPayment()
        )
            .isInstanceOf(
                AccessDeniedException.class
            );
    }

    @Test
    @WithAnonymousUser
    void rejectsAnonymousUsers() {
        assertThatThrownBy(
            () ->
                securedOperationsService
                    .inspectFailedPayment()
        )
            .isInstanceOf(
                AccessDeniedException.class
            );
    }

    static class SecuredOperationsService {

        @PreAuthorize(
            "hasAnyRole('OPERATIONS', 'ADMIN')"
        )
        String inspectFailedPayment() {
            return "failed-payment-details";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        SecuredOperationsService
        securedOperationsService() {
            return new SecuredOperationsService();
        }
    }
}
