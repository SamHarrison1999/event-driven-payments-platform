package com.samharrison.payments.account.internal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties =
        "platform.portfolio-demo.auto-provisioning.enabled=true"
)
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class PortfolioDemoOnboardingHttpIntegrationTest {

    private static final String
        REGISTRATION_ENDPOINT =
        "/api/v1/identity/registrations";

    private static final String
        SESSION_ENDPOINT =
        "/api/v1/identity/session";

    private static final String
        ACCOUNT_ENDPOINT =
        "/api/v1/accounts";

    private static final String
        SESSION_COOKIE_NAME =
        "PAYMENTS_SESSION";

    private static final String PASSWORD =
        "this is a secure portfolio passphrase";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_portfolio_onboarding_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationCreatesUsableDemoWorkspace()
        throws Exception {

        String email =
            "portfolio-customer@example.com";

        mockMvc.perform(
                post(REGISTRATION_ENDPOINT)
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """
                            .formatted(
                                email,
                                PASSWORD
                            )
                    )
            )
            .andExpect(status().isCreated());

        MvcResult loginResult =
            mockMvc.perform(
                    post(SESSION_ENDPOINT)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "email": "%s",
                              "password": "%s"
                            }
                            """
                                .formatted(
                                    email,
                                    PASSWORD
                                )
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie =
            loginResult
                .getResponse()
                .getCookie(
                    SESSION_COOKIE_NAME
                );

        Assertions.assertThat(
            sessionCookie
        ).isNotNull();

        mockMvc.perform(
                get(ACCOUNT_ENDPOINT)
                    .cookie(sessionCookie)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$", hasSize(2))
            )
            .andExpect(
                jsonPath("$[0].currency")
                    .value("GBP")
            )
            .andExpect(
                jsonPath(
                    "$[0].balanceMinorUnits"
                ).value(100000)
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$[1].currency")
                    .value("GBP")
            )
            .andExpect(
                jsonPath(
                    "$[1].balanceMinorUnits"
                ).value(100000)
            )
            .andExpect(
                jsonPath("$[1].status")
                    .value("ACTIVE")
            );
    }
}