package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdentitySessionIntegrationTest {

    private static final String SESSION_ENDPOINT =
        "/api/v1/identity/session";

    private static final String SESSION_COOKIE_NAME =
        "PAYMENTS_SESSION";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_session_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRegistrationService
        registrationService;

    @Autowired
    private IdentityUserRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStoredData() {
        jdbcTemplate.update(
            "DELETE FROM spring_session_attributes"
        );

        jdbcTemplate.update(
            "DELETE FROM spring_session"
        );

        repository.deleteAll();
        repository.flush();
    }

    @Test
    void persistsAndInvalidatesAnAuthenticatedSession()
        throws Exception {
        String email =
            "Sam.Customer@Example.COM";

        String password =
            "this is a secure customer passphrase";

        CustomerRegistrationResult registration =
            registrationService.register(
                email,
                password
            );

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
                              "email":
                                "sam.customer@example.com",
                              "password":
                                "this is a secure customer passphrase"
                            }
                            """
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                    header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                    )
                )
                .andExpect(
                    jsonPath("$.userId")
                        .value(
                            registration.id()
                                .toString()
                        )
                )
                .andExpect(
                    jsonPath("$.email")
                        .value(email)
                )
                .andExpect(
                    jsonPath("$.roles[0]")
                        .value("CUSTOMER")
                )
                .andReturn();

        Cookie sessionCookie =
            loginResult
                .getResponse()
                .getCookie(
                    SESSION_COOKIE_NAME
                );

        assertThat(sessionCookie)
            .isNotNull();

        assertThat(sessionCookie.isHttpOnly())
            .isTrue();

        assertThat(sessionCookie.getPath())
            .isEqualTo("/api");

        assertThat(storedSessionCount())
            .isEqualTo(1L);

        mockMvc.perform(
                get(SESSION_ENDPOINT)
                    .cookie(sessionCookie)
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.userId")
                    .value(
                        registration.id()
                            .toString()
                    )
            )
            .andExpect(
                jsonPath("$.email")
                    .value(email)
            )
            .andExpect(
                jsonPath("$.roles[0]")
                    .value("CUSTOMER")
            );

        mockMvc.perform(
                delete(SESSION_ENDPOINT)
                    .cookie(sessionCookie)
                    .with(csrf())
            )
            .andExpect(status().isNoContent())
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            );

        assertThat(storedSessionCount())
            .isZero();

        mockMvc.perform(
                get(SESSION_ENDPOINT)
                    .cookie(sessionCookie)
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    @Test
    void rejectsLoginWithoutCsrf()
        throws Exception {
        MvcResult result =
            mockMvc.perform(
                    post(SESSION_ENDPOINT)
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "email":
                                "sam.customer@example.com",
                              "password":
                                "this is a secure customer passphrase"
                            }
                            """
                        )
                )
                .andExpect(status().isForbidden())
                .andReturn();

        Cookie anonymousSessionCookie =
            result
                .getResponse()
                .getCookie(
                    SESSION_COOKIE_NAME
                );

        assertThat(anonymousSessionCookie)
            .isNotNull();

        assertThat(storedSessionCount())
            .isEqualTo(1L);

        mockMvc.perform(
                get(SESSION_ENDPOINT)
                    .cookie(
                        anonymousSessionCookie
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    @Test
    void rejectsInvalidCredentialsWithoutCreatingSession()
        throws Exception {
        registrationService.register(
            "sam.customer@example.com",
            "this is a secure customer passphrase"
        );

        MvcResult result =
            mockMvc.perform(
                    post(SESSION_ENDPOINT)
                        .with(csrf())
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "email":
                                "sam.customer@example.com",
                              "password":
                                "this password is incorrect"
                            }
                            """
                        )
                )
                .andExpect(
                    status().isUnauthorized()
                )
                .andExpect(
                    header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                    )
                )
                .andReturn();

        assertThat(
            result
                .getResponse()
                .getCookie(
                    SESSION_COOKIE_NAME
                )
        )
            .isNull();

        assertThat(storedSessionCount())
            .isZero();
    }

    @Test
    void rejectsAnUnauthenticatedCurrentSessionRequest()
        throws Exception {
        mockMvc.perform(
                get(SESSION_ENDPOINT)
            )
            .andExpect(
                status().isUnauthorized()
            );

        assertThat(storedSessionCount())
            .isZero();
    }

    @Test
    void rejectsLogoutWithoutCsrf()
        throws Exception {
        String email =
            "sam.customer@example.com";

        String password =
            "this is a secure customer passphrase";

        registrationService.register(
            email,
            password
        );

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
                              "email":
                                "sam.customer@example.com",
                              "password":
                                "this is a secure customer passphrase"
                            }
                            """
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

        assertThat(sessionCookie)
            .isNotNull();

        assertThat(storedSessionCount())
            .isEqualTo(1L);

        mockMvc.perform(
                delete(SESSION_ENDPOINT)
                    .cookie(sessionCookie)
            )
            .andExpect(status().isForbidden());

        assertThat(storedSessionCount())
            .isEqualTo(1L);

        mockMvc.perform(
                get(SESSION_ENDPOINT)
                    .cookie(sessionCookie)
            )
            .andExpect(status().isOk());
    }

    private Long storedSessionCount() {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM spring_session
            """,
            Long.class
        );
    }
}
