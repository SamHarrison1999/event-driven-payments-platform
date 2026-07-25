package com.samharrison.payments.reconciliation.internal;

import com.samharrison.payments.reconciliation.OperationalReconciliationReportReader;
import com.samharrison.payments.reconciliation.ReconciliationOperationalSummary;
import com.samharrison.payments.reconciliation.ReconciliationReportQuery;
import com.samharrison.payments.reconciliation.ReconciliationReportRow;
import com.samharrison.payments.reconciliation.SettlementOperationalSummary;
import com.samharrison.payments.reconciliation.SettlementReportRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class OperationalReconciliationReportReaderService
    implements OperationalReconciliationReportReader {

    private static final String SETTLEMENT_SUMMARY = """
        SELECT
            COUNT(*) AS import_count,
            COALESCE(SUM(row_count), 0) AS row_count,
            COALESCE(SUM(matched_count), 0)
                AS matched_count,
            COALESCE(SUM(discrepancy_count), 0)
                AS discrepancy_count,
            COUNT(*) FILTER (
                WHERE discrepancy_count = 0
            ) AS all_matched_count,
            COUNT(*) FILTER (
                WHERE discrepancy_count > 0
            ) AS with_discrepancies_count
        FROM settlement_import
        WHERE status = 'COMPLETED'
          AND completed_at >= :from
          AND completed_at < :to
        """;

    private static final String DISCREPANCY_COUNTS = """
        SELECT code, status, COUNT(*) AS item_count
        FROM settlement_discrepancy
        WHERE created_at >= :from
          AND created_at < :to
        GROUP BY code, status
        """;

    private static final String RESOLUTION_COUNTS = """
        SELECT decision, COUNT(*) AS item_count
        FROM settlement_resolution
        WHERE decided_at >= :from
          AND decided_at < :to
        GROUP BY decision
        """;

    private static final String OPEN_AGE_COUNTS = """
        SELECT
            COUNT(*) FILTER (
                WHERE created_at >=
                    :to - INTERVAL '1 day'
            ) AS under_one_day,
            COUNT(*) FILTER (
                WHERE created_at <
                    :to - INTERVAL '1 day'
                  AND created_at >=
                    :to - INTERVAL '7 days'
            ) AS one_to_seven_days,
            COUNT(*) FILTER (
                WHERE created_at <
                    :to - INTERVAL '7 days'
                  AND created_at >=
                    :to - INTERVAL '30 days'
            ) AS eight_to_thirty_days,
            COUNT(*) FILTER (
                WHERE created_at <
                    :to - INTERVAL '30 days'
            ) AS over_thirty_days
        FROM settlement_discrepancy
        WHERE status = 'OPEN'
          AND created_at >= :from
          AND created_at < :to
        """;

    private static final String SETTLEMENT_ROWS = """
        SELECT
            settlement_import.id AS import_id,
            settlement_record.row_number,
            settlement_record.settlement_record_id,
            settlement_record.payment_id,
            settlement_record.amount_minor_units,
            settlement_record.currency,
            settlement_record.settled_at,
            settlement_result.outcome,
            settlement_result.discrepancy_code,
            settlement_import.completed_at
        FROM settlement_import
        JOIN settlement_record
          ON settlement_record.settlement_import_id =
            settlement_import.id
        JOIN settlement_result
          ON settlement_result.settlement_record_id =
            settlement_record.id
        WHERE settlement_import.status = 'COMPLETED'
          AND settlement_import.completed_at >= :from
          AND settlement_import.completed_at < :to
        ORDER BY
            settlement_import.completed_at ASC,
            settlement_import.id ASC,
            settlement_record.row_number ASC
        LIMIT :limit
        """;

    private static final String RECONCILIATION_ROWS = """
        SELECT
            settlement_discrepancy.id
                AS discrepancy_id,
            settlement_discrepancy.settlement_import_id,
            settlement_record.settlement_record_id,
            settlement_discrepancy.code,
            settlement_discrepancy.status,
            settlement_discrepancy.created_at,
            settlement_resolution.decision,
            settlement_resolution.decided_at,
            settlement_resolution.actor_identity_user_id
        FROM settlement_discrepancy
        JOIN settlement_record
          ON settlement_record.id =
            settlement_discrepancy.settlement_record_id
        LEFT JOIN settlement_resolution
          ON settlement_resolution
                .settlement_discrepancy_id =
            settlement_discrepancy.id
        WHERE settlement_discrepancy.created_at >= :from
          AND settlement_discrepancy.created_at < :to
        ORDER BY
            settlement_discrepancy.created_at ASC,
            settlement_discrepancy.id ASC
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    OperationalReconciliationReportReaderService(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public SettlementOperationalSummary
        summarizeSettlements(
            ReconciliationReportQuery query
        ) {
        MapSqlParameterSource parameters =
            parameters(query);
        SettlementOperationalSummary summary =
            jdbcTemplate.queryForObject(
                SETTLEMENT_SUMMARY,
                parameters,
                OperationalReconciliationReportReaderService
                    ::mapSettlementSummary
            );

        if (summary == null) {
            throw new IllegalStateException(
                "Settlement summary query returned "
                    + "no row."
            );
        }

        return summary;
    }

    @Override
    public ReconciliationOperationalSummary
        summarizeReconciliation(
            ReconciliationReportQuery query
        ) {
        MapSqlParameterSource parameters =
            parameters(query);
        Map<String, Long> codeCounts =
            zeroDiscrepancyCodes();
        Map<String, Long> stateCounts =
            zeroLifecycleStates();

        jdbcTemplate.query(
            DISCREPANCY_COUNTS,
            parameters,
            (RowCallbackHandler) resultSet -> {
                codeCounts.put(
                    resultSet.getString("code"),
                    codeCounts.get(
                        resultSet.getString("code")
                    )
                        + resultSet.getLong(
                            "item_count"
                        )
                );
                stateCounts.put(
                    resultSet.getString("status"),
                    stateCounts.get(
                        resultSet.getString("status")
                    )
                        + resultSet.getLong(
                            "item_count"
                        )
                );
            }
        );

        Map<String, Long> decisionCounts =
            zeroResolutionDecisions();

        jdbcTemplate.query(
            RESOLUTION_COUNTS,
            parameters,
            (RowCallbackHandler) resultSet ->
                decisionCounts.put(
                resultSet.getString("decision"),
                resultSet.getLong("item_count")
            )
        );

        Map<String, Long> ageCounts =
            jdbcTemplate.queryForObject(
                OPEN_AGE_COUNTS,
                parameters,
                OperationalReconciliationReportReaderService
                    ::mapAgeCounts
            );

        if (ageCounts == null) {
            throw new IllegalStateException(
                "Open age summary query returned "
                    + "no row."
            );
        }

        return new ReconciliationOperationalSummary(
            codeCounts,
            stateCounts,
            decisionCounts,
            ageCounts
        );
    }

    @Override
    public List<SettlementReportRow>
        readSettlementRows(
            ReconciliationReportQuery query
        ) {
        MapSqlParameterSource parameters =
            parameters(query);
        parameters.addValue("limit", query.limit());

        return jdbcTemplate.query(
            SETTLEMENT_ROWS,
            parameters,
            OperationalReconciliationReportReaderService
                ::mapSettlementRow
        );
    }

    @Override
    public List<ReconciliationReportRow>
        readReconciliationRows(
            ReconciliationReportQuery query
        ) {
        MapSqlParameterSource parameters =
            parameters(query);
        parameters.addValue("limit", query.limit());

        return jdbcTemplate.query(
            RECONCILIATION_ROWS,
            parameters,
            OperationalReconciliationReportReaderService
                ::mapReconciliationRow
        );
    }

    private static MapSqlParameterSource parameters(
        ReconciliationReportQuery query
    ) {
        ReconciliationReportQuery requiredQuery =
            Objects.requireNonNull(
                query,
                "query must not be null"
            );

        return new MapSqlParameterSource()
            .addValue(
                "from",
                requiredQuery
                    .from()
                    .atOffset(ZoneOffset.UTC)
            )
            .addValue(
                "to",
                requiredQuery
                    .to()
                    .atOffset(ZoneOffset.UTC)
            );
    }

    private static SettlementOperationalSummary
        mapSettlementSummary(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        return new SettlementOperationalSummary(
            resultSet.getLong("import_count"),
            resultSet.getLong("row_count"),
            resultSet.getLong("matched_count"),
            resultSet.getLong("discrepancy_count"),
            Map.of(
                "ALL_MATCHED",
                resultSet.getLong(
                    "all_matched_count"
                ),
                "WITH_DISCREPANCIES",
                resultSet.getLong(
                    "with_discrepancies_count"
                )
            )
        );
    }

    private static Map<String, Long> mapAgeCounts(
        ResultSet resultSet,
        int rowIndex
    ) throws SQLException {
        Map<String, Long> counts =
            new LinkedHashMap<>();
        counts.put(
            "UNDER_ONE_DAY",
            resultSet.getLong("under_one_day")
        );
        counts.put(
            "ONE_TO_SEVEN_DAYS",
            resultSet.getLong("one_to_seven_days")
        );
        counts.put(
            "EIGHT_TO_THIRTY_DAYS",
            resultSet.getLong(
                "eight_to_thirty_days"
            )
        );
        counts.put(
            "OVER_THIRTY_DAYS",
            resultSet.getLong("over_thirty_days")
        );
        return counts;
    }

    private static SettlementReportRow
        mapSettlementRow(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        return new SettlementReportRow(
            resultSet.getObject(
                "import_id",
                java.util.UUID.class
            ),
            resultSet.getInt("row_number"),
            resultSet.getString(
                "settlement_record_id"
            ),
            resultSet.getObject(
                "payment_id",
                java.util.UUID.class
            ),
            resultSet.getLong("amount_minor_units"),
            resultSet.getString("currency"),
            resultSet
                .getTimestamp("settled_at")
                .toInstant(),
            resultSet.getString("outcome"),
            resultSet.getString("discrepancy_code"),
            resultSet
                .getTimestamp("completed_at")
                .toInstant()
        );
    }

    private static ReconciliationReportRow
        mapReconciliationRow(
            ResultSet resultSet,
            int rowIndex
        ) throws SQLException {
        java.sql.Timestamp decidedAt =
            resultSet.getTimestamp("decided_at");

        return new ReconciliationReportRow(
            resultSet.getObject(
                "discrepancy_id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "settlement_import_id",
                java.util.UUID.class
            ),
            resultSet.getString(
                "settlement_record_id"
            ),
            resultSet.getString("code"),
            resultSet.getString("status"),
            resultSet
                .getTimestamp("created_at")
                .toInstant(),
            resultSet.getString("decision"),
            decidedAt == null
                ? null
                : decidedAt.toInstant(),
            resultSet.getObject(
                "actor_identity_user_id",
                java.util.UUID.class
            )
        );
    }

    private static Map<String, Long>
        zeroDiscrepancyCodes() {
        Map<String, Long> counts =
            new LinkedHashMap<>();

        for (
            SettlementDiscrepancyCode code
                : SettlementDiscrepancyCode.values()
        ) {
            counts.put(code.name(), 0L);
        }

        return counts;
    }

    private static Map<String, Long>
        zeroLifecycleStates() {
        Map<String, Long> counts =
            new LinkedHashMap<>();

        for (
            SettlementDiscrepancyStatus status
                : SettlementDiscrepancyStatus.values()
        ) {
            counts.put(status.name(), 0L);
        }

        return counts;
    }

    private static Map<String, Long>
        zeroResolutionDecisions() {
        Map<String, Long> counts =
            new LinkedHashMap<>();

        for (
            SettlementResolutionDecision decision
                : SettlementResolutionDecision.values()
        ) {
            counts.put(decision.name(), 0L);
        }

        return counts;
    }
}
