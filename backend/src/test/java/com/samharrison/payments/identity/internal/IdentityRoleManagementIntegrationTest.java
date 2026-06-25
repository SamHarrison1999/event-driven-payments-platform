package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
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
class IdentityRoleManagementIntegrationTest {

    private static final String SESSION_ENDPOINT =
        "/api/v1/identity/session";

    private static final String SESSION_COOKIE_NAME =
        "PAYMENTS_SESSION";

    private static final String PASSWORD =
        "this is a secure customer passphrase";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_role_management_test"
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
    void administratorCanGrantRoleAndRevokeTargetSessions()
        throws Exception {
        CustomerRegistrationResult administrator =
            registrationService.register(
                "administrator@example.com",
                PASSWORD
            );

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        grantRoleDirectly(
            administrator.id(),
            IdentityRole.ADMIN
        );

        Cookie targetSession =
            login("target@example.com");

        Cookie administratorSession =
            login("administrator@example.com");

        mockMvc.perform(
                put(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.OPERATIONS
                    )
                )
                    .cookie(
                        administratorSession
                    )
                    .with(csrf())
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
                        target.id().toString()
                    )
            )
            .andExpect(
                jsonPath("$.roles")
                    .value(
                        containsInAnyOrder(
                            "CUSTOMER",
                            "OPERATIONS"
                        )
                    )
            );

        IdentityUser updatedTarget =
            repository
                .findById(target.id())
                .orElseThrow();

        assertThat(updatedTarget.roles())
            .containsExactlyInAnyOrder(
                IdentityRole.CUSTOMER,
                IdentityRole.OPERATIONS
            );

        mockMvc.perform(
                get(SESSION_ENDPOINT)
                    .cookie(targetSession)
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    @Test
    void administratorCanRevokeOneOfSeveralRoles()
        throws Exception {
        CustomerRegistrationResult administrator =
            registrationService.register(
                "administrator@example.com",
                PASSWORD
            );

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        grantRoleDirectly(
            administrator.id(),
            IdentityRole.ADMIN
        );

        grantRoleDirectly(
            target.id(),
            IdentityRole.OPERATIONS
        );

        Cookie administratorSession =
            login("administrator@example.com");

        mockMvc.perform(
                delete(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.CUSTOMER
                    )
                )
                    .cookie(
                        administratorSession
                    )
                    .with(csrf())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.roles[0]")
                    .value("OPERATIONS")
            );

        IdentityUser updatedTarget =
            repository
                .findById(target.id())
                .orElseThrow();

        assertThat(updatedTarget.roles())
            .containsExactly(
                IdentityRole.OPERATIONS
            );
    }

    @Test
    void customerCannotManageRoles()
        throws Exception {
        registrationService.register(
            "customer@example.com",
            PASSWORD
        );

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        Cookie customerSession =
            login("customer@example.com");

        mockMvc.perform(
                put(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.OPERATIONS
                    )
                )
                    .cookie(customerSession)
                    .with(csrf())
            )
            .andExpect(
                status().isForbidden()
            );

        IdentityUser unchangedTarget =
            repository
                .findById(target.id())
                .orElseThrow();

        assertThat(unchangedTarget.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }

    @Test
    void anonymousUserCannotManageRoles()
        throws Exception {
        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        mockMvc.perform(
                put(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.OPERATIONS
                    )
                )
                    .with(csrf())
            )
            .andExpect(
                status().isUnauthorized()
            );

        IdentityUser unchangedTarget =
            repository
                .findById(target.id())
                .orElseThrow();

        assertThat(unchangedTarget.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }

    @Test
    void administratorCannotRemoveFinalRole()
        throws Exception {
        CustomerRegistrationResult administrator =
            registrationService.register(
                "administrator@example.com",
                PASSWORD
            );

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        grantRoleDirectly(
            administrator.id(),
            IdentityRole.ADMIN
        );

        Cookie administratorSession =
            login("administrator@example.com");

        mockMvc.perform(
                delete(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.CUSTOMER
                    )
                )
                    .cookie(
                        administratorSession
                    )
                    .with(csrf())
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            );

        IdentityUser unchangedTarget =
            repository
                .findById(target.id())
                .orElseThrow();

        assertThat(unchangedTarget.roles())
            .containsExactly(
                IdentityRole.CUSTOMER
            );
    }

    @Test
    void returnsNotFoundForUnknownIdentityUser()
        throws Exception {
        CustomerRegistrationResult administrator =
            registrationService.register(
                "administrator@example.com",
                PASSWORD
            );

        grantRoleDirectly(
            administrator.id(),
            IdentityRole.ADMIN
        );

        Cookie administratorSession =
            login("administrator@example.com");

        UUID missingUserId =
            UUID.fromString(
                "abdd9365-dd23-414f-9f34-90537a5adb48"
            );

        mockMvc.perform(
                put(
                    roleEndpoint(
                        missingUserId,
                        IdentityRole.OPERATIONS
                    )
                )
                    .cookie(
                        administratorSession
                    )
                    .with(csrf())
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                header().string(
                    HttpHeaders.CACHE_CONTROL,
                    containsString("no-store")
                )
            );
    }

    private void grantRoleDirectly(
        UUID userId,
        IdentityRole role
    ) {
        IdentityUser user =
            repository
                .findById(userId)
                .orElseThrow();

        user.grantRole(
            role,
            Instant.now()
        );

        repository.saveAndFlush(user);
    }

    private Cookie login(String email)
        throws Exception {
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
            result
                .getResponse()
                .getCookie(
                    SESSION_COOKIE_NAME
                );

        assertThat(sessionCookie)
            .isNotNull();

        return sessionCookie;
    }

    private static String roleEndpoint(
        UUID userId,
        IdentityRole role
    ) {
        return (
            "/api/v1/identity/users/%s/roles/%s"
        )
            .formatted(
                userId,
                role.name()
            );
    }
}
