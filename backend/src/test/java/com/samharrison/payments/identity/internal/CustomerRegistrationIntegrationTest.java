package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerRegistrationIntegrationTest {

    private static final String ENDPOINT =
        "/api/v1/identity/registrations";

    private static final String VALID_PASSWORD =
        "this is a secure passphrase";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_registration_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserRepository repository;

    @Autowired
    private PasswordHashingService hashingService;

    @BeforeEach
    void clearIdentityData() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void registersACustomerWithoutExposingSecrets()
        throws Exception {
        String rawPassword = VALID_PASSWORD;

        MvcResult result = mockMvc.perform(
                post(ENDPOINT).with(csrf()).with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email":
                            "Sam.Customer@Example.COM",
                          "password":
                            "this is a secure passphrase"
                        }
                        """
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            )
            .andExpect(
                jsonPath("$.id").isNotEmpty()
            )
            .andExpect(
                jsonPath("$.email")
                    .value(
                        "Sam.Customer@Example.COM"
                    )
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.roles[0]")
                    .value("CUSTOMER")
            )
            .andExpect(
                jsonPath("$.createdAt")
                    .isNotEmpty()
            )
            .andReturn();

        String responseBody = result
            .getResponse()
            .getContentAsString();

        assertThat(responseBody)
            .doesNotContain(rawPassword)
            .doesNotContain("passwordHash");

        IdentityUser storedUser = repository
            .findByNormalizedEmail(
                "sam.customer@example.com"
            )
            .orElseThrow();

        assertThat(storedUser.passwordHash())
            .startsWith(
                "{"
                    + PasswordHashingConfiguration
                    .CURRENT_ENCODING_ID
                    + "}"
            )
            .doesNotContain(rawPassword);

        assertThat(
            hashingService.matches(
                rawPassword,
                storedUser.passwordHash()
            )
        )
            .isTrue();

        assertThat(storedUser.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively()
        throws Exception {
        mockMvc.perform(
                post(ENDPOINT).with(csrf()).with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email":
                            "Sam.Customer@Example.COM",
                          "password":
                            "this is a secure passphrase"
                        }
                        """
                    )
            )
            .andExpect(status().isCreated());

        mockMvc.perform(
                post(ENDPOINT).with(csrf()).with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email":
                            "sam.customer@example.com",
                          "password":
                            "another secure passphrase"
                        }
                        """
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType
                        .APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDENTITY_EMAIL_"
                            + "ALREADY_REGISTERED"
                    )
            )
            .andExpect(
                jsonPath("$.type")
                    .value(
                        "urn:problem:identity:"
                            + "email-already-registered"
                    )
            );

        assertThat(repository.count())
            .isEqualTo(1L);
    }

    @Test
    void rejectsAnInvalidEmailAddress()
        throws Exception {
        mockMvc.perform(
                post(ENDPOINT).with(csrf()).with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email": "not-an-email",
                          "password":
                            "this is a secure passphrase"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType
                        .APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDENTITY_REGISTRATION_INVALID"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                )
                    .value("email")
            );

        assertThat(repository.count())
            .isZero();
    }

    @Test
    void rejectsAPasswordPolicyViolation()
        throws Exception {
        String rejectedPassword = "tiny";

        MvcResult result = mockMvc.perform(
                post(ENDPOINT).with(csrf()).with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email":
                            "sam.customer@example.com",
                          "password": "tiny"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType
                        .APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDENTITY_PASSWORD_TOO_SHORT"
                    )
            )
            .andExpect(
                jsonPath("$.type")
                    .value(
                        "urn:problem:identity:"
                            + "password-too-short"
                    )
            )
            .andReturn();

        assertThat(
            result
                .getResponse()
                .getContentAsString()
        )
            .doesNotContain(rejectedPassword);

        assertThat(repository.count())
            .isZero();
    }

    @Test
    void rejectsAMissingPassword()
        throws Exception {
        mockMvc.perform(
                post(ENDPOINT).with(csrf()).with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email":
                            "sam.customer@example.com"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType
                        .APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDENTITY_REGISTRATION_INVALID"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                )
                    .value("password")
            );

        assertThat(repository.count())
            .isZero();
    }
}
