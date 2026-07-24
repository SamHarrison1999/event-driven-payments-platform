package com.samharrison.payments.reconciliation.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SettlementMatchClaimStore {

    private static final String CLAIM_SQL = """
        INSERT INTO settlement_match_claim (
            payment_id,
            settlement_record_id,
            claimed_at
        )
        VALUES (?, ?, ?)
        ON CONFLICT (payment_id) DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    SettlementMatchClaimStore(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    boolean claim(
        ImportedSettlementRecord record,
        Instant claimedAt
    ) {
        ImportedSettlementRecord requiredRecord =
            Objects.requireNonNull(
                record,
                "record must not be null"
            );

        return jdbcTemplate.update(
            CLAIM_SQL,
            requiredRecord.paymentId(),
            requiredRecord.id(),
            Timestamp.from(
                Objects.requireNonNull(
                    claimedAt,
                    "claimedAt must not be null"
                )
            )
        ) == 1;
    }
}
