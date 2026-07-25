package com.samharrison.payments.audit.internal;

import com.samharrison.payments.audit.BusinessAuditActorKind;
import com.samharrison.payments.audit.BusinessAuditEvidence;
import com.samharrison.payments.audit.BusinessAuditEvidenceReader;
import com.samharrison.payments.audit.BusinessAuditReadCriteria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class BusinessAuditEvidenceReaderService
    implements BusinessAuditEvidenceReader {

    private static final String SELECT = """
        SELECT
            id,
            event_type,
            schema_version,
            occurred_at,
            actor_kind,
            actor_identity_user_id,
            subject_type,
            subject_identifier,
            correlation_identifier,
            metadata
        FROM business_audit_event
        WHERE 1 = 1
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    BusinessAuditEvidenceReaderService(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public List<BusinessAuditEvidence> read(
        BusinessAuditReadCriteria criteria
    ) {
        BusinessAuditReadCriteria requiredCriteria =
            Objects.requireNonNull(
                criteria,
                "criteria must not be null"
            );

        StringBuilder query =
            new StringBuilder(SELECT);

        MapSqlParameterSource parameters =
            new MapSqlParameterSource();

        addFilters(
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
            BusinessAuditEvidenceReaderService
                ::mapEvidence
        );
    }

    private static void addFilters(
        StringBuilder query,
        MapSqlParameterSource parameters,
        BusinessAuditReadCriteria criteria
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

        query.append(
            " AND event_type IN (:eventTypes)"
        );
        parameters.addValue(
            "eventTypes",
            criteria.eventTypes()
        );

        if (
            criteria.actorIdentityUserId() != null
        ) {
            query.append(
                """
                 AND actor_identity_user_id =
                    :actorIdentityUserId
                """
            );
            parameters.addValue(
                "actorIdentityUserId",
                criteria.actorIdentityUserId()
            );
        }

        addExactFilter(
            query,
            parameters,
            "subject_type",
            "subjectType",
            criteria.subjectType()
        );
        addExactFilter(
            query,
            parameters,
            "subject_identifier",
            "subjectIdentifier",
            criteria.subjectIdentifier()
        );
        addExactFilter(
            query,
            parameters,
            "correlation_identifier",
            "correlationIdentifier",
            criteria.correlationIdentifier()
        );

        if (criteria.cursorOccurredAt() != null) {
            query.append(
                """
                 AND (
                    occurred_at < :cursorOccurredAt
                    OR (
                        occurred_at = :cursorOccurredAt
                        AND (
                            'BUSINESS_AUDIT:'
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
    }

    private static void addExactFilter(
        StringBuilder query,
        MapSqlParameterSource parameters,
        String column,
        String parameter,
        String value
    ) {
        if (value != null) {
            query
                .append(" AND ")
                .append(column)
                .append(" = :")
                .append(parameter);
            parameters.addValue(parameter, value);
        }
    }

    private static BusinessAuditEvidence mapEvidence(
        ResultSet resultSet,
        int rowIndex
    ) throws SQLException {
        return new BusinessAuditEvidence(
            resultSet.getObject(
                "id",
                java.util.UUID.class
            ),
            resultSet.getString("event_type"),
            resultSet.getInt("schema_version"),
            resultSet
                .getTimestamp("occurred_at")
                .toInstant(),
            BusinessAuditActorKind.valueOf(
                resultSet.getString("actor_kind")
            ),
            resultSet.getObject(
                "actor_identity_user_id",
                java.util.UUID.class
            ),
            resultSet.getString("subject_type"),
            resultSet.getString(
                "subject_identifier"
            ),
            resultSet.getString(
                "correlation_identifier"
            ),
            resultSet.getString("metadata")
        );
    }
}
