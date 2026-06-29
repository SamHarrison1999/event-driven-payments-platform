package com.samharrison.payments.ledger;

import com.samharrison.payments.shared.GbpAmount;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerQueryService {

    private final JdbcTemplate jdbcTemplate;

    public LedgerQueryService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PostedLedgerTransaction findTransaction(
        UUID transactionId
    ) {
        UUID requiredTransactionId =
            Objects.requireNonNull(
                transactionId,
                "transactionId must not be null"
            );

        TransactionHeader header =
            findHeader(requiredTransactionId);

        List<PostedLedgerEntry> entries =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    ledger_account_id,
                    side,
                    amount_minor_units,
                    entry_sequence,
                    description
                FROM ledger_entry
                WHERE transaction_id = ?
                ORDER BY entry_sequence ASC
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new PostedLedgerEntry(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "ledger_account_id",
                            UUID.class
                        ),
                        LedgerEntrySide.valueOf(
                            resultSet.getString("side")
                        ),
                        GbpAmount.ofMinorUnits(
                            resultSet.getLong(
                                "amount_minor_units"
                            )
                        ),
                        resultSet.getInt(
                            "entry_sequence"
                        ),
                        resultSet.getString(
                            "description"
                        )
                    ),
                requiredTransactionId
            );

        return new PostedLedgerTransaction(
            header.id(),
            header.transactionType(),
            header.businessReference(),
            header.correctsTransactionId(),
            header.postedAt(),
            header.description(),
            entries
        );
    }

    @Transactional(readOnly = true)
    public List<LedgerAccountEntry> findAccountEntries(
        UUID accountId
    ) {
        UUID requiredAccountId =
            requireAccount(accountId);

        return jdbcTemplate.query(
            """
            SELECT
                entry.id AS entry_id,
                entry.transaction_id,
                transaction.transaction_type,
                transaction.business_reference,
                transaction.posted_at,
                entry.side,
                entry.amount_minor_units,
                entry.entry_sequence,
                entry.description
            FROM ledger_entry AS entry
            INNER JOIN ledger_transaction AS transaction
                ON transaction.id = entry.transaction_id
            WHERE entry.ledger_account_id = ?
            ORDER BY
                transaction.posted_at DESC,
                transaction.id DESC,
                entry.entry_sequence ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new LedgerAccountEntry(
                    resultSet.getObject(
                        "entry_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "transaction_id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "transaction_type"
                    ),
                    resultSet.getString(
                        "business_reference"
                    ),
                    toInstant(
                        resultSet,
                        "posted_at"
                    ),
                    LedgerEntrySide.valueOf(
                        resultSet.getString("side")
                    ),
                    GbpAmount.ofMinorUnits(
                        resultSet.getLong(
                            "amount_minor_units"
                        )
                    ),
                    resultSet.getInt(
                        "entry_sequence"
                    ),
                    resultSet.getString(
                        "description"
                    )
                ),
            requiredAccountId
        );
    }

    @Transactional(readOnly = true)
    public LedgerBalanceVerification verifyAccountBalance(
        UUID accountId
    ) {
        UUID requiredAccountId =
            requireAccount(accountId);

        Long snapshotMinorUnits =
            jdbcTemplate.queryForObject(
                """
                SELECT balance_minor_units
                FROM customer_account
                WHERE id = ?
                """,
                Long.class,
                requiredAccountId
            );

        SideTotals totals =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(
                        SUM(amount_minor_units)
                            FILTER (WHERE side = 'DEBIT'),
                        0
                    ) AS debit_total,
                    COALESCE(
                        SUM(amount_minor_units)
                            FILTER (WHERE side = 'CREDIT'),
                        0
                    ) AS credit_total
                FROM ledger_entry
                WHERE ledger_account_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new SideTotals(
                        exactLong(
                            resultSet.getBigDecimal(
                                "debit_total"
                            )
                        ),
                        exactLong(
                            resultSet.getBigDecimal(
                                "credit_total"
                            )
                        )
                    ),
                requiredAccountId
            );

        long requiredSnapshot =
            Objects.requireNonNull(
                snapshotMinorUnits,
                "snapshot balance must not be null"
            );

        SideTotals requiredTotals =
            Objects.requireNonNull(
                totals,
                "ledger totals must not be null"
            );

        boolean consistent =
            BigDecimal
                .valueOf(requiredSnapshot)
                .add(
                    BigDecimal.valueOf(
                        requiredTotals.debits()
                    )
                )
                .compareTo(
                    BigDecimal.valueOf(
                        requiredTotals.credits()
                    )
                )
                == 0;

        return new LedgerBalanceVerification(
            requiredAccountId,
            GbpAmount.ofMinorUnits(
                requiredSnapshot
            ),
            GbpAmount.ofMinorUnits(
                requiredTotals.debits()
            ),
            GbpAmount.ofMinorUnits(
                requiredTotals.credits()
            ),
            consistent
        );
    }

    private TransactionHeader findHeader(
        UUID transactionId
    ) {
        List<TransactionHeader> headers =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    transaction_type,
                    business_reference,
                    corrects_transaction_id,
                    posted_at,
                    description
                FROM ledger_transaction
                WHERE id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new TransactionHeader(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "transaction_type"
                        ),
                        resultSet.getString(
                            "business_reference"
                        ),
                        resultSet.getObject(
                            "corrects_transaction_id",
                            UUID.class
                        ),
                        toInstant(
                            resultSet,
                            "posted_at"
                        ),
                        resultSet.getString(
                            "description"
                        )
                    ),
                transactionId
            );

        if (headers.isEmpty()) {
            throw new LedgerTransactionNotFoundException(
                transactionId
            );
        }

        return headers.getFirst();
    }

    private UUID requireAccount(
        UUID accountId
    ) {
        UUID requiredAccountId =
            Objects.requireNonNull(
                accountId,
                "accountId must not be null"
            );

        Boolean exists =
            jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM customer_account
                    WHERE id = ?
                )
                """,
                Boolean.class,
                requiredAccountId
            );

        if (!Boolean.TRUE.equals(exists)) {
            throw new LedgerAccountNotFoundException(
                requiredAccountId
            );
        }

        return requiredAccountId;
    }

    private static long exactLong(
        BigDecimal value
    ) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                "Ledger aggregate exceeds the "
                    + "supported minor-unit range.",
                exception
            );
        }
    }

    private static java.time.Instant toInstant(
        ResultSet resultSet,
        String columnName
    ) throws SQLException {
        return resultSet
            .getObject(
                columnName,
                OffsetDateTime.class
            )
            .toInstant();
    }

    private record TransactionHeader(
        UUID id,
        String transactionType,
        String businessReference,
        UUID correctsTransactionId,
        java.time.Instant postedAt,
        String description
    ) {
    }

    private record SideTotals(
        long debits,
        long credits
    ) {
    }
}