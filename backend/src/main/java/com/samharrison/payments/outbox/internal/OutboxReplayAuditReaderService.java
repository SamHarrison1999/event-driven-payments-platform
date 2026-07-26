package com.samharrison.payments.outbox.internal;

import com.samharrison.payments.outbox.OutboxReplayAuditEvidence;
import com.samharrison.payments.outbox.OutboxReplayAuditQuery;
import com.samharrison.payments.outbox.OutboxReplayAuditReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class OutboxReplayAuditReaderService
    implements OutboxReplayAuditReader {

    private static final String EVENT_TYPE =
        "outbox.dead-letter-replayed";

    private static final String SELECT = """
        SELECT
            id,
            event_id,
            actor_identity_user_id,
            event_version_before,
            replayed_at
        FROM outbox_replay_audit
        WHERE 1 = 1
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    OutboxReplayAuditReaderService(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public List<OutboxReplayAuditEvidence> read(
        OutboxReplayAuditQuery criteria
    ) {
        OutboxReplayAuditQuery requiredCriteria =
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
                        .equals("outbox_event")
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
                 AND CAST(event_id AS TEXT) =
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
             ORDER BY replayed_at DESC, id DESC
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
            OutboxReplayAuditReaderService
                ::mapEvidence
        );
    }

    private static void addTimeFilters(
        StringBuilder query,
        MapSqlParameterSource parameters,
        OutboxReplayAuditQuery criteria
    ) {
        if (criteria.from() != null) {
            query.append(
                " AND replayed_at >= :from"
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
                " AND replayed_at < :to"
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
        OutboxReplayAuditQuery criteria
    ) {
        if (criteria.cursorOccurredAt() == null) {
            return;
        }

        query.append(
            """
             AND (
                replayed_at < :cursorOccurredAt
                OR (
                    replayed_at = :cursorOccurredAt
                    AND (
                        'OUTBOX_REPLAY:'
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

    private static OutboxReplayAuditEvidence
        mapEvidence(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        return new OutboxReplayAuditEvidence(
            resultSet.getObject(
                "id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "event_id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "actor_identity_user_id",
                java.util.UUID.class
            ),
            resultSet.getLong(
                "event_version_before"
            ),
            resultSet
                .getTimestamp("replayed_at")
                .toInstant()
        );
    }
}
