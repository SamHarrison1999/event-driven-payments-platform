package com.samharrison.payments.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
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
class IdentitySecurityAuditIntegrationTest {

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
                "payments_security_audit_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRegistrationService
        registrationService;

    @Autowired
    private IdentityUserRepository userRepository;

    @Autowired
    private IdentitySecurityEventRepository
        securityEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStoredData() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE identity_security_event"
        );

        jdbcTemplate.update(
            "DELETE FROM spring_session_attributes"
        );

        jdbcTemplate.update(
            "DELETE FROM spring_session"
        );

        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    void grantingRoleWritesSecurityEvent()
        throws Exception {
        CustomerRegistrationResult administrator =
            registerAdministrator();

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        Cookie administratorSession =
            login("administrator@example.com");

        mockMvc.perform(
                put(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.OPERATIONS
                    )
                )
                    .cookie(administratorSession)
                    .with(csrf())
            )
            .andExpect(status().isOk());

        List<IdentitySecurityEvent> events =
            securityEventRepository
                .findAllBySubjectUserIdOrderByOccurredAtAsc(
                    target.id()
                );

        assertThat(events)
            .hasSize(1);

        IdentitySecurityEvent event =
            events.get(0);

        assertThat(event.eventType())
            .isEqualTo(
                IdentitySecurityEventType.ROLE_GRANTED
            );

        assertThat(event.actorUserId())
            .isEqualTo(administrator.id());

        assertThat(event.subjectUserId())
            .isEqualTo(target.id());

        assertThat(event.role())
            .isEqualTo(
                IdentityRole.OPERATIONS
            );

        assertThat(event.occurredAt())
            .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void revokingRoleWritesSecurityEvent()
        throws Exception {
        CustomerRegistrationResult administrator =
            registerAdministrator();

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
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
                    .cookie(administratorSession)
                    .with(csrf())
            )
            .andExpect(status().isOk());

        List<IdentitySecurityEvent> events =
            securityEventRepository
                .findAllBySubjectUserIdOrderByOccurredAtAsc(
                    target.id()
                );

        assertThat(events)
            .hasSize(1);

        IdentitySecurityEvent event =
            events.get(0);

        assertThat(event.eventType())
            .isEqualTo(
                IdentitySecurityEventType.ROLE_REVOKED
            );

        assertThat(event.actorUserId())
            .isEqualTo(administrator.id());

        assertThat(event.subjectUserId())
            .isEqualTo(target.id());

        assertThat(event.role())
            .isEqualTo(
                IdentityRole.CUSTOMER
            );
    }

    @Test
    void idempotentGrantDoesNotWriteEvent()
        throws Exception {
        registerAdministrator();

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        Cookie administratorSession =
            login("administrator@example.com");

        mockMvc.perform(
                put(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.CUSTOMER
                    )
                )
                    .cookie(administratorSession)
                    .with(csrf())
            )
            .andExpect(status().isOk());

        assertThat(
            securityEventRepository
                .findAllBySubjectUserIdOrderByOccurredAtAsc(
                    target.id()
                )
        )
            .isEmpty();
    }

    @Test
    void databaseRejectsAuditMutation()
        throws Exception {
        registerAdministrator();

        CustomerRegistrationResult target =
            registrationService.register(
                "target@example.com",
                PASSWORD
            );

        Cookie administratorSession =
            login("administrator@example.com");

        mockMvc.perform(
                put(
                    roleEndpoint(
                        target.id(),
                        IdentityRole.OPERATIONS
                    )
                )
                    .cookie(administratorSession)
                    .with(csrf())
            )
            .andExpect(status().isOk());

        IdentitySecurityEvent event =
            securityEventRepository
                .findAllBySubjectUserIdOrderByOccurredAtAsc(
                    target.id()
                )
                .get(0);

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE identity_security_event
                    SET role_code = 'ADMIN'
                    WHERE id = ?
                    """,
                    event.id()
                )
        )
            .isInstanceOf(
                DataAccessException.class
            );

        assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    DELETE FROM identity_security_event
                    WHERE id = ?
                    """,
                    event.id()
                )
        )
            .isInstanceOf(
                DataAccessException.class
            );

        assertThat(
            securityEventRepository.existsById(
                event.id()
            )
        )
            .isTrue();
    }

    private CustomerRegistrationResult
    registerAdministrator() {
        CustomerRegistrationResult administrator =
            registrationService.register(
                "administrator@example.com",
                PASSWORD
            );

        grantRoleDirectly(
            administrator.id(),
            IdentityRole.ADMIN
        );

        return administrator;
    }

    private void grantRoleDirectly(
        UUID userId,
        IdentityRole role
    ) {
        IdentityUser user =
            userRepository
                .findById(userId)
                .orElseThrow();

        user.grantRole(
            role,
            Instant.now()
        );

        userRepository.saveAndFlush(user);
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
