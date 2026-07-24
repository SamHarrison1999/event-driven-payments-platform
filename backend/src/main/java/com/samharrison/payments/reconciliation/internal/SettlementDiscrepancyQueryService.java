package com.samharrison.payments.reconciliation.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementDiscrepancyQueryService {

    private static final String SELECT_COLUMNS = """
        SELECT
            discrepancy.id AS discrepancy_id,
            discrepancy.settlement_import_id,
            discrepancy.code,
            discrepancy.status,
            discrepancy.created_at,
            discrepancy.version,
            record.row_number,
            record.settlement_record_id,
            record.payment_id,
            record.amount_minor_units,
            record.currency,
            record.settled_at,
            resolution.id AS resolution_id,
            resolution.actor_identity_user_id,
            resolution.decision,
            resolution.reason,
            resolution.discrepancy_version,
            resolution.decided_at
        FROM settlement_discrepancy discrepancy
        JOIN settlement_record record
          ON record.id =
                discrepancy.settlement_record_id
        LEFT JOIN settlement_resolution resolution
          ON resolution.settlement_discrepancy_id =
                discrepancy.id
        """;

    private static final String FIND_QUERY =
        SELECT_COLUMNS
            + """
            WHERE discrepancy.id = ?
            """;

    private static final String FIRST_PAGE_QUERY =
        SELECT_COLUMNS
            + """
            WHERE discrepancy.status = ?
            ORDER BY
                discrepancy.created_at ASC,
                discrepancy.id ASC
            LIMIT ?
            """;

    private static final String NEXT_PAGE_QUERY =
        SELECT_COLUMNS
            + """
            WHERE discrepancy.status = ?
              AND (
                    discrepancy.created_at > ?
                    OR (
                        discrepancy.created_at = ?
                        AND discrepancy.id > ?
                    )
              )
            ORDER BY
                discrepancy.created_at ASC,
                discrepancy.id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    SettlementDiscrepancyQueryService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', 'ADMIN')"
    )
    SettlementDiscrepancyResponse find(
        UUID discrepancyId
    ) {
        UUID requiredId =
            Objects.requireNonNull(
                discrepancyId,
                "discrepancyId must not be null"
            );

        return jdbcTemplate.query(
                FIND_QUERY,
                SettlementDiscrepancyQueryService
                    ::mapDiscrepancy,
                requiredId
            )
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new
                    SettlementDiscrepancyNotFoundException(
                        requiredId
                    )
            );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', 'ADMIN')"
    )
    SettlementDiscrepancyPageResponse findPage(
        SettlementDiscrepancyStatus status,
        Instant afterCreatedAt,
        UUID afterId,
        int limit
    ) {
        SettlementDiscrepancyStatus requiredStatus =
            Objects.requireNonNull(
                status,
                "status must not be null"
            );

        if (
            (afterCreatedAt == null)
                != (afterId == null)
        ) {
            throw new
                InvalidSettlementDiscrepancyQueryException();
        }

        List<SettlementDiscrepancyResponse> fetched =
            afterCreatedAt == null
                ? jdbcTemplate.query(
                    FIRST_PAGE_QUERY,
                    SettlementDiscrepancyQueryService
                        ::mapDiscrepancy,
                    requiredStatus.name(),
                    limit + 1
                )
                : jdbcTemplate.query(
                    NEXT_PAGE_QUERY,
                    SettlementDiscrepancyQueryService
                        ::mapDiscrepancy,
                    requiredStatus.name(),
                    afterCreatedAt.atOffset(
                        ZoneOffset.UTC
                    ),
                    afterCreatedAt.atOffset(
                        ZoneOffset.UTC
                    ),
                    afterId,
                    limit + 1
                );

        boolean hasMore = fetched.size() > limit;
        List<SettlementDiscrepancyResponse> content =
            hasMore
                ? List.copyOf(
                    fetched.subList(0, limit)
                )
                : List.copyOf(fetched);

        SettlementDiscrepancyResponse last =
            hasMore
                ? content.getLast()
                : null;

        return new SettlementDiscrepancyPageResponse(
            content,
            last == null
                ? null
                : last.createdAt(),
            last == null
                ? null
                : last.discrepancyId()
        );
    }

    private static SettlementDiscrepancyResponse
        mapDiscrepancy(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        UUID resolutionId =
            resultSet.getObject(
                "resolution_id",
                UUID.class
            );

        SettlementResolutionResponse resolution =
            resolutionId == null
                ? null
                : new SettlementResolutionResponse(
                    resolutionId,
                    resultSet.getObject(
                        "actor_identity_user_id",
                        UUID.class
                    ),
                    resultSet.getString("decision"),
                    resultSet.getString("reason"),
                    resultSet.getLong(
                        "discrepancy_version"
                    ),
                    resultSet
                        .getTimestamp("decided_at")
                        .toInstant()
                );

        return new SettlementDiscrepancyResponse(
            resultSet.getObject(
                "discrepancy_id",
                UUID.class
            ),
            resultSet.getObject(
                "settlement_import_id",
                UUID.class
            ),
            resultSet.getInt("row_number"),
            resultSet.getString(
                "settlement_record_id"
            ),
            resultSet.getObject(
                "payment_id",
                UUID.class
            ),
            resultSet.getLong("amount_minor_units"),
            resultSet.getString("currency"),
            resultSet
                .getTimestamp("settled_at")
                .toInstant(),
            resultSet.getString("code"),
            resultSet.getString("status"),
            resultSet
                .getTimestamp("created_at")
                .toInstant(),
            resultSet.getLong("version"),
            resolution
        );
    }
}
