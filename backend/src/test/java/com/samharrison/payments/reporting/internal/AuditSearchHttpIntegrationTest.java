package com.samharrison.payments.reporting.internal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class AuditSearchHttpIntegrationTest {

    private static final String ENDPOINT =
        "/api/v1/audit-events";

    private static final Instant FROM =
        Instant.parse(
            "2026-07-25T00:00:00Z"
        );

    private static final Instant TO =
        Instant.parse(
            "2026-07-26T00:00:00Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "audit_search_http_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearAuditEvidence() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE "
                + "business_audit_event, "
                + "identity_security_event"
        );
    }

    @Test
    void operationsPaginationExcludesNewerSettlement()
        throws Exception {
        insertSettlementEvent(
            Instant.parse(
                "2026-07-25T15:00:00Z"
            )
        );
        UUID customerEventId =
            insertCustomerEvent(
                Instant.parse(
                    "2026-07-25T14:00:00Z"
                )
            );
        UUID paymentEventId =
            insertPaymentEvent(
                Instant.parse(
                    "2026-07-25T13:00:00Z"
                )
            );

        MvcResult firstPage =
            mockMvc.perform(
                    get(ENDPOINT)
                        .with(
                            user("operations")
                                .roles("OPERATIONS")
                        )
                        .param(
                            "from",
                            FROM.toString()
                        )
                        .param(
                            "to",
                            TO.toString()
                        )
                        .param("limit", "1")
                )
                .andExpect(status().isOk())
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
                    jsonPath("$.events.length()")
                        .value(1)
                )
                .andExpect(
                    jsonPath("$.events[0].eventId")
                        .value(
                            "BUSINESS_AUDIT:"
                                + customerEventId
                        )
                )
                .andExpect(
                    jsonPath("$.events[0].category")
                        .value("CUSTOMER")
                )
                .andExpect(
                    jsonPath("$.nextCursor")
                        .isString()
                )
                .andReturn();

        JsonNode body =
            objectMapper.readTree(
                firstPage
                    .getResponse()
                    .getContentAsString()
            );
        String cursor =
            body
                .get("nextCursor")
                .stringValue();

        mockMvc.perform(
                get(ENDPOINT)
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
                    )
                    .param(
                        "from",
                        FROM.toString()
                    )
                    .param(
                        "to",
                        TO.toString()
                    )
                    .param("limit", "1")
                    .param("cursor", cursor)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.events[0].eventId")
                    .value(
                        "BUSINESS_AUDIT:"
                            + paymentEventId
                    )
            )
            .andExpect(
                jsonPath("$.nextCursor")
                    .doesNotExist()
            );
    }

    @Test
    void adminReadsSourceOwnedIdentityEvidence()
        throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO identity_security_event (
                id,
                event_type,
                actor_user_id,
                subject_user_id,
                role_code,
                occurred_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            eventId,
            "ROLE_GRANTED",
            actorId,
            subjectId,
            "ADMIN",
            Instant.parse(
                    "2026-07-25T12:00:00Z"
                )
                .atOffset(ZoneOffset.UTC)
        );

        mockMvc.perform(
                get(ENDPOINT)
                    .with(
                        user("administrator")
                            .roles("ADMIN")
                    )
                    .param(
                        "category",
                        "IDENTITY_SECURITY"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.events[0].eventId")
                    .value(
                        "IDENTITY_SECURITY:"
                            + eventId
                    )
            )
            .andExpect(
                jsonPath("$.events[0].eventType")
                    .value("identity.role-granted")
            )
            .andExpect(
                jsonPath("$.events[0].actorIdentityUserId")
                    .value(actorId.toString())
            )
            .andExpect(
                jsonPath("$.events[0].subjectIdentifier")
                    .value(subjectId.toString())
            )
            .andExpect(
                jsonPath("$.events[0].details.role")
                    .value("ADMIN")
            )
            .andExpect(
                jsonPath(
                    "$.events[0].correlationIdentifier"
                )
                    .doesNotExist()
            );
    }

    @Test
    void customerCannotAccessAuditSearch()
        throws Exception {
        mockMvc.perform(
                get(ENDPOINT)
                    .with(
                        user("customer")
                            .roles("CUSTOMER")
                    )
                    .param("category", "PAYMENT")
            )
            .andExpect(status().isForbidden())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SECURITY_ACCESS_DENIED")
            );
    }

    @Test
    void rejectsEmptyAndCursorMismatchedSearches()
        throws Exception {
        mockMvc.perform(
                get(ENDPOINT)
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("AUDIT_QUERY_INVALID")
            );

        insertCustomerEvent(
            Instant.parse(
                "2026-07-25T14:00:00Z"
            )
        );
        insertPaymentEvent(
            Instant.parse(
                "2026-07-25T13:00:00Z"
            )
        );

        MvcResult firstPage =
            mockMvc.perform(
                    get(ENDPOINT)
                        .with(
                            user("operations")
                                .roles("OPERATIONS")
                        )
                        .param(
                            "from",
                            FROM.toString()
                        )
                        .param(
                            "to",
                            TO.toString()
                        )
                        .param("limit", "1")
                )
                .andExpect(status().isOk())
                .andReturn();

        String cursor =
            objectMapper
                .readTree(
                    firstPage
                        .getResponse()
                        .getContentAsString()
                )
                .get("nextCursor")
                .stringValue();

        mockMvc.perform(
                get(ENDPOINT)
                    .with(
                        user("operations")
                            .roles("OPERATIONS")
                    )
                    .param(
                        "from",
                        FROM.toString()
                    )
                    .param(
                        "to",
                        TO.toString()
                    )
                    .param("limit", "2")
                    .param("cursor", cursor)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("AUDIT_QUERY_INVALID")
            );
    }

    private UUID insertCustomerEvent(
        Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        insertBusinessEvent(
            eventId,
            "customer.created",
            occurredAt,
            "IDENTITY_USER",
            UUID.randomUUID(),
            "customer",
            customerId.toString(),
            "customer",
            "customer",
            customerId.toString(),
            "created",
            "customer-correlation-" + eventId,
            "{\"status\":\"ACTIVE\"}"
        );

        return eventId;
    }

    private UUID insertPaymentEvent(
        Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        insertBusinessEvent(
            eventId,
            "payment.submitted",
            occurredAt,
            "IDENTITY_USER",
            UUID.randomUUID(),
            "payment",
            paymentId.toString(),
            "payment",
            "payment",
            paymentId.toString(),
            "submitted",
            "payment-correlation-" + eventId,
            """
            {
              "amountMinor": 250,
              "currency": "GBP",
              "destinationAccountId":
                "123e4567-e89b-12d3-a456-426614174001",
              "sourceAccountId":
                "123e4567-e89b-12d3-a456-426614174002"
            }
            """
        );

        return eventId;
    }

    private void insertSettlementEvent(
        Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        insertBusinessEvent(
            eventId,
            "settlement.import-accepted",
            occurredAt,
            "IDENTITY_USER",
            UUID.randomUUID(),
            "settlement_import",
            importId.toString(),
            "reconciliation",
            "settlement_import",
            importId.toString(),
            "accepted",
            "settlement-correlation-" + eventId,
            """
            {
              "discrepancyCount": 0,
              "matchedCount": 1,
              "rowCount": 1
            }
            """
        );
    }

    private void insertBusinessEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String actorKind,
        UUID actorIdentityUserId,
        String subjectType,
        String subjectIdentifier,
        String sourceModule,
        String sourceRecordType,
        String sourceRecordIdentifier,
        String sourceEventIdentifier,
        String correlationIdentifier,
        String metadata
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO business_audit_event (
                id,
                event_type,
                schema_version,
                occurred_at,
                recorded_at,
                actor_kind,
                actor_identity_user_id,
                subject_type,
                subject_identifier,
                source_module,
                source_record_type,
                source_record_identifier,
                source_event_identifier,
                correlation_identifier,
                metadata
            )
            VALUES (
                ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?
            )
            """,
            eventId,
            eventType,
            occurredAt.atOffset(ZoneOffset.UTC),
            occurredAt.atOffset(ZoneOffset.UTC),
            actorKind,
            actorIdentityUserId,
            subjectType,
            subjectIdentifier,
            sourceModule,
            sourceRecordType,
            sourceRecordIdentifier,
            sourceEventIdentifier,
            correlationIdentifier,
            metadata
        );
    }
}
