package com.samharrison.payments.reconciliation.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementImportQueryService {

    private static final String RESULT_QUERY = """
        SELECT
            sr.row_number,
            sr.settlement_record_id,
            sr.payment_id,
            sr.amount_minor_units,
            sr.currency,
            sr.settled_at,
            rr.outcome,
            rr.discrepancy_code,
            rr.reconciled_at
        FROM settlement_record sr
        JOIN settlement_result rr
          ON rr.settlement_record_id = sr.id
        WHERE sr.settlement_import_id = ?
          AND sr.row_number > ?
        ORDER BY sr.row_number ASC
        LIMIT ?
        """;

    private final SettlementImportRepository repository;

    private final JdbcTemplate jdbcTemplate;

    SettlementImportQueryService(
        SettlementImportRepository repository,
        JdbcTemplate jdbcTemplate
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );
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
    SettlementImportResponse find(
        UUID importId
    ) {
        return SettlementImportResponse.completed(
            requireImport(importId),
            false
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', 'ADMIN')"
    )
    SettlementResultPageResponse findResults(
        UUID importId,
        int afterRowNumber,
        int limit
    ) {
        requireImport(importId);

        List<SettlementResultResponse> fetched =
            jdbcTemplate.query(
                RESULT_QUERY,
                SettlementImportQueryService::mapResult,
                importId,
                afterRowNumber,
                limit + 1
            );

        boolean hasMore = fetched.size() > limit;
        List<SettlementResultResponse> content =
            hasMore
                ? List.copyOf(
                    fetched.subList(0, limit)
                )
                : List.copyOf(fetched);

        Integer nextAfterRowNumber =
            hasMore
                ? content
                    .get(content.size() - 1)
                    .rowNumber()
                : null;

        return new SettlementResultPageResponse(
            content,
            nextAfterRowNumber
        );
    }

    private SettlementImport requireImport(
        UUID importId
    ) {
        UUID requiredId =
            Objects.requireNonNull(
                importId,
                "importId must not be null"
            );

        return repository
            .findById(requiredId)
            .filter(
                candidate ->
                    candidate.status()
                        == SettlementImportStatus.COMPLETED
            )
            .orElseThrow(
                () ->
                    new SettlementImportNotFoundException(
                        requiredId
                    )
            );
    }

    private static SettlementResultResponse mapResult(
        ResultSet resultSet,
        int rowIndex
    ) throws SQLException {
        return new SettlementResultResponse(
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
            resultSet.getString("outcome"),
            resultSet.getString("discrepancy_code"),
            resultSet
                .getTimestamp("reconciled_at")
                .toInstant()
        );
    }
}
