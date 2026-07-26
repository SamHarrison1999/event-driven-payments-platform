package com.samharrison.payments.identity.internal;

import com.samharrison.payments.identity.IdentitySecurityAuditEvidence;
import com.samharrison.payments.identity.IdentitySecurityAuditQuery;
import com.samharrison.payments.identity.IdentitySecurityAuditReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class IdentitySecurityAuditReaderService
    implements IdentitySecurityAuditReader {

    private static final String SELECT = """
        SELECT
            id,
            event_type,
            actor_user_id,
            subject_user_id,
            role_code,
            occurred_at
        FROM identity_security_event
        WHERE 1 = 1
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    IdentitySecurityAuditReaderService(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public List<IdentitySecurityAuditEvidence> read(
        IdentitySecurityAuditQuery criteria
    ) {
        IdentitySecurityAuditQuery requiredCriteria =
            Objects.requireNonNull(
                criteria,
                "query must not be null"
            );

        if (
            requiredCriteria.correlationIdentifier()
                != null
            || (
                requiredCriteria.subjectType() != null
                    && !requiredCriteria
                        .subjectType()
                        .equals("identity_user")
            )
        ) {
            return List.of();
        }

        List<String> storedEventTypes =
            requiredCriteria
                .eventTypes()
                .stream()
                .map(
                    IdentitySecurityAuditReaderService
                        ::storedEventType
                )
                .filter(Objects::nonNull)
                .toList();

        if (storedEventTypes.isEmpty()) {
            return List.of();
        }

        StringBuilder query =
            new StringBuilder(SELECT);
        MapSqlParameterSource parameters =
            new MapSqlParameterSource();

        addTimeFilters(
            query,
            parameters,
            requiredCriteria
        );

        query.append(
            " AND event_type IN (:eventTypes)"
        );
        parameters.addValue(
            "eventTypes",
            storedEventTypes
        );

        if (
            requiredCriteria.actorIdentityUserId()
                != null
        ) {
            query.append(
                " AND actor_user_id = :actorId"
            );
            parameters.addValue(
                "actorId",
                requiredCriteria
                    .actorIdentityUserId()
            );
        }

        if (
            requiredCriteria.subjectIdentifier()
                != null
        ) {
            query.append(
                """
                 AND CAST(subject_user_id AS TEXT) =
                    :subjectIdentifier
                """
            );
            parameters.addValue(
                "subjectIdentifier",
                requiredCriteria
                    .subjectIdentifier()
            );
        }

        addCursor(
            query,
            parameters,
            requiredCriteria
        );

        query.append(
            """
             ORDER BY occurred_at DESC, id DESC
             LIMIT :limit
            """
        );
        parameters.addValue(
            "limit",
            requiredCriteria.limit()
        );

        return jdbcTemplate.query(
            query.toString(),
            parameters,
            IdentitySecurityAuditReaderService
                ::mapEvidence
        );
    }

    private static void addTimeFilters(
        StringBuilder query,
        MapSqlParameterSource parameters,
        IdentitySecurityAuditQuery criteria
    ) {
        if (criteria.from() != null) {
            query.append(
                " AND occurred_at >= :from"
            );
            parameters.addValue(
                "from",
                criteria
                    .from()
                    .atOffset(ZoneOffset.UTC)
            );
        }

        if (criteria.to() != null) {
            query.append(
                " AND occurred_at < :to"
            );
            parameters.addValue(
                "to",
                criteria
                    .to()
                    .atOffset(ZoneOffset.UTC)
            );
        }
    }

    private static void addCursor(
        StringBuilder query,
        MapSqlParameterSource parameters,
        IdentitySecurityAuditQuery criteria
    ) {
        if (criteria.cursorOccurredAt() == null) {
            return;
        }

        query.append(
            """
             AND (
                occurred_at < :cursorOccurredAt
                OR (
                    occurred_at = :cursorOccurredAt
                    AND (
                        'IDENTITY_SECURITY:'
                        || CAST(id AS TEXT)
                    ) < :cursorEventId
                )
             )
            """
        );
        parameters.addValue(
            "cursorOccurredAt",
            criteria
                .cursorOccurredAt()
                .atOffset(ZoneOffset.UTC)
        );
        parameters.addValue(
            "cursorEventId",
            criteria.cursorEventId()
        );
    }

    private static String storedEventType(
        String normalizedEventType
    ) {
        if (normalizedEventType == null) {
            return null;
        }

        return switch (normalizedEventType) {
            case "identity.role-granted" ->
                "ROLE_GRANTED";
            case "identity.role-revoked" ->
                "ROLE_REVOKED";
            default -> null;
        };
    }

    private static String normalizedEventType(
        String storedEventType
    ) {
        return switch (storedEventType) {
            case "ROLE_GRANTED" ->
                "identity.role-granted";
            case "ROLE_REVOKED" ->
                "identity.role-revoked";
            default ->
                throw new IllegalStateException(
                    "Unsupported identity audit event type."
                );
        };
    }

    private static IdentitySecurityAuditEvidence
        mapEvidence(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        return new IdentitySecurityAuditEvidence(
            resultSet.getObject(
                "id",
                java.util.UUID.class
            ),
            normalizedEventType(
                resultSet.getString("event_type")
            ),
            resultSet.getObject(
                "actor_user_id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "subject_user_id",
                java.util.UUID.class
            ),
            resultSet.getString("role_code"),
            resultSet
                .getTimestamp("occurred_at")
                .toInstant()
        );
    }
}
