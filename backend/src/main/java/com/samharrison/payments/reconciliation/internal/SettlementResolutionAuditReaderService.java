package com.samharrison.payments.reconciliation.internal;

import com.samharrison.payments.reconciliation.SettlementResolutionAuditEvidence;
import com.samharrison.payments.reconciliation.SettlementResolutionAuditQuery;
import com.samharrison.payments.reconciliation.SettlementResolutionAuditReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class SettlementResolutionAuditReaderService
    implements SettlementResolutionAuditReader {

    private static final String EVENT_TYPE =
        "reconciliation.discrepancy-resolved";

    private static final String SELECT = """
        SELECT
            id,
            settlement_discrepancy_id,
            actor_identity_user_id,
            decision,
            discrepancy_version,
            decided_at
        FROM settlement_resolution
        WHERE 1 = 1
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    SettlementResolutionAuditReaderService(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public List<SettlementResolutionAuditEvidence>
        read(
            SettlementResolutionAuditQuery criteria
        ) {
        SettlementResolutionAuditQuery requiredCriteria =
            Objects.requireNonNull(
                criteria,
                "query must not be null"
            );

        if (
            (
                !requiredCriteria
                    .eventTypes()
                    .contains(EVENT_TYPE)
            )
            || requiredCriteria
                .correlationIdentifier() != null
            || (
                requiredCriteria.subjectType() != null
                    && !requiredCriteria
                        .subjectType()
                        .equals(
                            "settlement_discrepancy"
                        )
            )
        ) {
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

        if (
            requiredCriteria.actorIdentityUserId()
                != null
        ) {
            query.append(
                """
                 AND actor_identity_user_id =
                    :actorIdentityUserId
                """
            );
            parameters.addValue(
                "actorIdentityUserId",
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
                 AND CAST(
                    settlement_discrepancy_id AS TEXT
                 ) = :subjectIdentifier
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
             ORDER BY decided_at DESC, id DESC
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
            SettlementResolutionAuditReaderService
                ::mapEvidence
        );
    }

    private static void addTimeFilters(
        StringBuilder query,
        MapSqlParameterSource parameters,
        SettlementResolutionAuditQuery criteria
    ) {
        if (criteria.from() != null) {
            query.append(
                " AND decided_at >= :from"
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
                " AND decided_at < :to"
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
        SettlementResolutionAuditQuery criteria
    ) {
        if (criteria.cursorOccurredAt() == null) {
            return;
        }

        query.append(
            """
             AND (
                decided_at < :cursorOccurredAt
                OR (
                    decided_at = :cursorOccurredAt
                    AND (
                        'SETTLEMENT_RESOLUTION:'
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

    private static SettlementResolutionAuditEvidence
        mapEvidence(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        return new SettlementResolutionAuditEvidence(
            resultSet.getObject(
                "id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "settlement_discrepancy_id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "actor_identity_user_id",
                java.util.UUID.class
            ),
            resultSet.getString("decision"),
            resultSet.getLong(
                "discrepancy_version"
            ),
            resultSet
                .getTimestamp("decided_at")
                .toInstant()
        );
    }
}
