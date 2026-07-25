package com.samharrison.payments.payment.internal;

import com.samharrison.payments.payment.PaymentOperationalReportReader;
import com.samharrison.payments.payment.PaymentOperationalSummary;
import com.samharrison.payments.payment.PaymentReportQuery;
import com.samharrison.payments.payment.PaymentReportRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
class PaymentOperationalReportReaderService
    implements PaymentOperationalReportReader {

    private static final String SUMMARY_QUERY = """
        SELECT
            COUNT(*) FILTER (
                WHERE created_at >= :from
                  AND created_at < :to
            ) AS submitted_count,
            COUNT(*) FILTER (
                WHERE status IN (
                    'COMPLETED',
                    'REJECTED',
                    'FAILED'
                )
                  AND updated_at >= :from
                  AND updated_at < :to
            ) AS terminal_count,
            COUNT(*) FILTER (
                WHERE status = 'COMPLETED'
                  AND updated_at >= :from
                  AND updated_at < :to
            ) AS completed_count,
            COUNT(*) FILTER (
                WHERE status = 'REJECTED'
                  AND updated_at >= :from
                  AND updated_at < :to
            ) AS rejected_count,
            COUNT(*) FILTER (
                WHERE status = 'FAILED'
                  AND updated_at >= :from
                  AND updated_at < :to
            ) AS failed_count,
            COALESCE(
                SUM(amount_minor_units) FILTER (
                    WHERE status = 'COMPLETED'
                      AND updated_at >= :from
                      AND updated_at < :to
                ),
                0
            ) AS completed_amount_minor_units
        FROM payment
        WHERE (
            created_at >= :from
            AND created_at < :to
        )
        OR (
            updated_at >= :from
            AND updated_at < :to
        )
        """;

    private static final String REJECTION_QUERY = """
        SELECT rejection_reason, COUNT(*) AS reason_count
        FROM payment
        WHERE status = 'REJECTED'
          AND updated_at >= :from
          AND updated_at < :to
        GROUP BY rejection_reason
        """;

    private static final String FAILURE_QUERY = """
        SELECT failure_reason, COUNT(*) AS reason_count
        FROM payment
        WHERE status = 'FAILED'
          AND updated_at >= :from
          AND updated_at < :to
        GROUP BY failure_reason
        """;

    private static final String ROW_QUERY = """
        SELECT
            id,
            actor_identity_id,
            source_account_id,
            destination_account_id,
            amount_minor_units,
            currency,
            status,
            ledger_transaction_id,
            rejection_reason,
            failure_reason,
            created_at,
            updated_at
        FROM payment
        WHERE created_at >= :from
          AND created_at < :to
        ORDER BY created_at ASC, id ASC
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    PaymentOperationalReportReaderService(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public PaymentOperationalSummary summarize(
        PaymentReportQuery query
    ) {
        PaymentReportQuery requiredQuery =
            Objects.requireNonNull(
                query,
                "query must not be null"
            );
        MapSqlParameterSource parameters =
            parameters(requiredQuery);

        SummaryCounts counts =
            jdbcTemplate.queryForObject(
                SUMMARY_QUERY,
                parameters,
                PaymentOperationalReportReaderService
                    ::mapSummary
            );

        if (counts == null) {
            throw new IllegalStateException(
                "Payment summary query returned no row."
            );
        }

        return new PaymentOperationalSummary(
            counts.submittedCount(),
            counts.terminalCount(),
            counts.completedCount(),
            counts.rejectedCount(),
            counts.failedCount(),
            counts.completedAmountMinorUnits(),
            readRejectionCounts(parameters),
            readFailureCounts(parameters)
        );
    }

    @Override
    public List<PaymentReportRow> readRows(
        PaymentReportQuery query
    ) {
        PaymentReportQuery requiredQuery =
            Objects.requireNonNull(
                query,
                "query must not be null"
            );
        MapSqlParameterSource parameters =
            parameters(requiredQuery);
        parameters.addValue(
            "limit",
            requiredQuery.limit()
        );

        return jdbcTemplate.query(
            ROW_QUERY,
            parameters,
            PaymentOperationalReportReaderService
                ::mapRow
        );
    }

    private Map<String, Long> readRejectionCounts(
        MapSqlParameterSource parameters
    ) {
        Map<String, Long> counts =
            new LinkedHashMap<>();

        for (
            PaymentRejectionReason reason
                : PaymentRejectionReason.values()
        ) {
            counts.put(reason.code(), 0L);
        }

        jdbcTemplate.query(
            REJECTION_QUERY,
            parameters,
            (RowCallbackHandler) resultSet -> counts.put(
                PaymentRejectionReason
                    .valueOf(
                        resultSet.getString(
                            "rejection_reason"
                        )
                    )
                    .code(),
                resultSet.getLong("reason_count")
            )
        );

        return counts;
    }

    private Map<String, Long> readFailureCounts(
        MapSqlParameterSource parameters
    ) {
        Map<String, Long> counts =
            new LinkedHashMap<>();

        for (
            PaymentFailureReason reason
                : PaymentFailureReason.values()
        ) {
            counts.put(reason.code(), 0L);
        }

        jdbcTemplate.query(
            FAILURE_QUERY,
            parameters,
            (RowCallbackHandler) resultSet -> counts.put(
                PaymentFailureReason
                    .valueOf(
                        resultSet.getString(
                            "failure_reason"
                        )
                    )
                    .code(),
                resultSet.getLong("reason_count")
            )
        );

        return counts;
    }

    private static MapSqlParameterSource parameters(
        PaymentReportQuery query
    ) {
        return new MapSqlParameterSource()
            .addValue(
                "from",
                query.from().atOffset(ZoneOffset.UTC)
            )
            .addValue(
                "to",
                query.to().atOffset(ZoneOffset.UTC)
            );
    }

    private static SummaryCounts mapSummary(
        ResultSet resultSet,
        int rowIndex
    ) throws SQLException {
        return new SummaryCounts(
            resultSet.getLong("submitted_count"),
            resultSet.getLong("terminal_count"),
            resultSet.getLong("completed_count"),
            resultSet.getLong("rejected_count"),
            resultSet.getLong("failed_count"),
            resultSet.getLong(
                "completed_amount_minor_units"
            )
        );
    }

    private static PaymentReportRow mapRow(
        ResultSet resultSet,
        int rowIndex
    ) throws SQLException {
        String rejectionReason =
            resultSet.getString("rejection_reason");
        String failureReason =
            resultSet.getString("failure_reason");

        return new PaymentReportRow(
            resultSet.getObject(
                "id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "actor_identity_id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "source_account_id",
                java.util.UUID.class
            ),
            resultSet.getObject(
                "destination_account_id",
                java.util.UUID.class
            ),
            resultSet.getLong("amount_minor_units"),
            resultSet.getString("currency"),
            resultSet.getString("status"),
            resultSet.getObject(
                "ledger_transaction_id",
                java.util.UUID.class
            ),
            rejectionReason == null
                ? null
                : PaymentRejectionReason
                    .valueOf(rejectionReason)
                    .code(),
            failureReason == null
                ? null
                : PaymentFailureReason
                    .valueOf(failureReason)
                    .code(),
            resultSet
                .getTimestamp("created_at")
                .toInstant(),
            resultSet
                .getTimestamp("updated_at")
                .toInstant()
        );
    }

    private record SummaryCounts(
        long submittedCount,
        long terminalCount,
        long completedCount,
        long rejectedCount,
        long failedCount,
        long completedAmountMinorUnits
    ) {
    }
}
