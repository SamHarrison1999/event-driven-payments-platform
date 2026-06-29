package com.samharrison.payments.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class LedgerPostingServiceIntegrationTest {

    private static final Instant ACCOUNT_TIME =
        Instant.parse("2026-06-29T15:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_ledger_posting_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private LedgerPostingService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                ledger_entry,
                ledger_transaction
            """
        );
        jdbcTemplate.update(
            "DELETE FROM customer_identity_assignment"
        );
        jdbcTemplate.update(
            "DELETE FROM customer_account"
        );
        jdbcTemplate.update(
            "DELETE FROM customer_profile"
        );
    }

    @Test
    void postsBalancedTransactionAtomically() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Posting Customer One"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Posting Customer Two"
            );

        List<LedgerPostingEntry> sourceEntries =
            new ArrayList<>(
                List.of(
                    entry(
                        debitAccountId,
                        LedgerEntrySide.DEBIT,
                        1_750L,
                        "Source debit"
                    ),
                    entry(
                        creditAccountId,
                        LedgerEntrySide.CREDIT,
                        1_750L,
                        "Destination credit"
                    )
                )
            );

        PostedLedgerTransaction posted =
            service.post(
                new LedgerPostingCommand(
                    "internal_transfer",
                    "payment-3001",
                    null,
                    "Internal payment posting",
                    sourceEntries
                )
            );

        sourceEntries.clear();

        assertThat(posted.id()).isNotNull();
        assertThat(posted.transactionType())
            .isEqualTo("INTERNAL_TRANSFER");
        assertThat(posted.businessReference())
            .isEqualTo("payment-3001");
        assertThat(posted.correctsTransactionId())
            .isNull();
        assertThat(posted.postedAt()).isNotNull();
        assertThat(posted.entries())
            .hasSize(2);
        assertThat(posted.entries())
            .extracting(PostedLedgerEntry::sequence)
            .containsExactly(1, 2);

        assertThatThrownBy(
            () -> posted.entries().clear()
        )
            .isInstanceOf(
                UnsupportedOperationException.class
            );

        Long headerCount =
            countRows(
                "ledger_transaction"
            );

        Long entryCount =
            countRows(
                "ledger_entry"
            );

        assertThat(headerCount).isEqualTo(1L);
        assertThat(entryCount).isEqualTo(2L);
    }

    @Test
    void rejectsUnbalancedPostingBeforePersistence() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Unbalanced Customer One"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Unbalanced Customer Two"
            );

        assertThatThrownBy(
            () ->
                service.post(
                    new LedgerPostingCommand(
                        "INTERNAL_TRANSFER",
                        null,
                        null,
                        "Unbalanced posting",
                        List.of(
                            entry(
                                debitAccountId,
                                LedgerEntrySide.DEBIT,
                                500L,
                                "Debit"
                            ),
                            entry(
                                creditAccountId,
                                LedgerEntrySide.CREDIT,
                                499L,
                                "Credit"
                            )
                        )
                    )
                )
        )
            .isInstanceOf(
                InvalidLedgerPostingException.class
            )
            .hasMessageContaining(
                "unbalanced"
            );

        assertThat(
            countRows("ledger_transaction")
        )
            .isZero();

        assertThat(
            countRows("ledger_entry")
        )
            .isZero();
    }

    @Test
    void rollsBackHeaderWhenAnEntryCannotPersist() {
        UUID validAccountId =
            insertCustomerAccount(
                "Rollback Customer"
            );

        UUID missingAccountId =
            UUID.randomUUID();

        assertThatThrownBy(
            () ->
                service.post(
                    new LedgerPostingCommand(
                        "INTERNAL_TRANSFER",
                        "payment-rollback",
                        null,
                        "Rollback posting",
                        List.of(
                            entry(
                                validAccountId,
                                LedgerEntrySide.DEBIT,
                                800L,
                                "Valid debit"
                            ),
                            entry(
                                missingAccountId,
                                LedgerEntrySide.CREDIT,
                                800L,
                                "Missing account credit"
                            )
                        )
                    )
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThat(
            countRows("ledger_transaction")
        )
            .isZero();

        assertThat(
            countRows("ledger_entry")
        )
            .isZero();
    }

    @Test
    void defensivelyCopiesCommandEntries() {
        UUID debitAccountId =
            UUID.randomUUID();

        UUID creditAccountId =
            UUID.randomUUID();

        List<LedgerPostingEntry> mutableEntries =
            new ArrayList<>(
                List.of(
                    entry(
                        debitAccountId,
                        LedgerEntrySide.DEBIT,
                        100L,
                        "Debit"
                    ),
                    entry(
                        creditAccountId,
                        LedgerEntrySide.CREDIT,
                        100L,
                        "Credit"
                    )
                )
            );

        LedgerPostingCommand command =
            new LedgerPostingCommand(
                "INTERNAL_TRANSFER",
                null,
                null,
                "Immutable command",
                mutableEntries
            );

        mutableEntries.clear();

        assertThat(command.entries())
            .hasSize(2);

        assertThatThrownBy(
            () -> command.entries().clear()
        )
            .isInstanceOf(
                UnsupportedOperationException.class
            );
    }

    private LedgerPostingEntry entry(
        UUID accountId,
        LedgerEntrySide side,
        long minorUnits,
        String description
    ) {
        return new LedgerPostingEntry(
            accountId,
            side,
            GbpAmount.ofMinorUnits(minorUnits),
            description
        );
    }

    private UUID insertCustomerAccount(
        String customerName
    ) {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO customer_profile (
                id,
                full_name,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            customerId,
            customerName,
            "ACTIVE",
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        jdbcTemplate.update(
            """
            INSERT INTO customer_account (
                id,
                customer_id,
                currency,
                balance_minor_units,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            customerId,
            "GBP",
            0L,
            "ACTIVE",
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return accountId;
    }

    private Long countRows(
        String tableName
    ) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName,
            Long.class
        );
    }
}