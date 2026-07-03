package com.samharrison.payments.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class LedgerQueryServiceIntegrationTest {

    private static final Instant ACCOUNT_TIME =
        Instant.parse("2026-06-29T17:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_ledger_query_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private LedgerPostingService postingService;

    @Autowired
    private LedgerQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                payment_idempotency,
                payment,
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
    void reloadsTransactionWithOrderedEntries() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Query Customer One"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Query Customer Two"
            );

        PostedLedgerTransaction posted =
            postingService.post(
                command(
                    "payment-query-1",
                    debitAccountId,
                    creditAccountId,
                    1_100L
                )
            );

        PostedLedgerTransaction reloaded =
            queryService.findTransaction(
                posted.id()
            );

        assertThat(reloaded.id())
            .isEqualTo(posted.id());
        assertThat(reloaded.transactionType())
            .isEqualTo(posted.transactionType());
        assertThat(reloaded.businessReference())
            .isEqualTo(posted.businessReference());
        assertThat(reloaded.correctsTransactionId())
            .isEqualTo(posted.correctsTransactionId());
        assertThat(reloaded.postedAt())
            .isBetween(
                posted.postedAt().minusNanos(999L),
                posted.postedAt().plusNanos(999L)
            );
        assertThat(reloaded.description())
            .isEqualTo(posted.description());
        assertThat(reloaded.entries())
            .isEqualTo(posted.entries());

        assertThat(reloaded.entries())
            .extracting(PostedLedgerEntry::sequence)
            .containsExactly(1, 2);
    }

    @Test
    void returnsDeterministicAccountHistory() {
        UUID targetAccountId =
            insertCustomerAccount(
                "History Customer"
            );

        UUID counterpartyAccountId =
            insertCustomerAccount(
                "History Counterparty"
            );

        PostedLedgerTransaction first =
            postingService.post(
                command(
                    "payment-history-1",
                    targetAccountId,
                    counterpartyAccountId,
                    300L
                )
            );

        LockSupport.parkNanos(2_000_000L);

        PostedLedgerTransaction second =
            postingService.post(
                command(
                    "payment-history-2",
                    counterpartyAccountId,
                    targetAccountId,
                    125L
                )
            );

        List<LedgerAccountEntry> history =
            queryService.findAccountEntries(
                targetAccountId
            );

        assertThat(history)
            .hasSize(2);

        assertThat(history)
            .extracting(
                LedgerAccountEntry::transactionId
            )
            .containsExactly(
                second.id(),
                first.id()
            );

        assertThat(history)
            .extracting(
                LedgerAccountEntry::side
            )
            .containsExactly(
                LedgerEntrySide.CREDIT,
                LedgerEntrySide.DEBIT
            );

        assertThat(history)
            .extracting(
                entry -> entry.amount().minorUnits()
            )
            .containsExactly(
                125L,
                300L
            );
    }

    @Test
    void verifiesSnapshotAgainstLedgerTotals() {
        UUID targetAccountId =
            insertCustomerAccount(
                "Verification Customer"
            );

        UUID counterpartyAccountId =
            insertCustomerAccount(
                "Verification Counterparty"
            );

        postingService.post(
            command(
                "payment-verification",
                counterpartyAccountId,
                targetAccountId,
                700L
            )
        );

        updateBalance(
            targetAccountId,
            700L
        );

        LedgerBalanceVerification consistent =
            queryService.verifyAccountBalance(
                targetAccountId
            );

        assertThat(consistent.snapshotBalance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(700L)
            );

        assertThat(consistent.totalDebits())
            .isEqualTo(GbpAmount.ZERO);

        assertThat(consistent.totalCredits())
            .isEqualTo(
                GbpAmount.ofMinorUnits(700L)
            );

        assertThat(consistent.consistent())
            .isTrue();

        updateBalance(
            targetAccountId,
            699L
        );

        LedgerBalanceVerification inconsistent =
            queryService.verifyAccountBalance(
                targetAccountId
            );

        assertThat(inconsistent.consistent())
            .isFalse();
    }

    @Test
    void verifiesEmptyAccountAtZero() {
        UUID accountId =
            insertCustomerAccount(
                "Empty Ledger Customer"
            );

        LedgerBalanceVerification verification =
            queryService.verifyAccountBalance(
                accountId
            );

        assertThat(verification.snapshotBalance())
            .isEqualTo(GbpAmount.ZERO);
        assertThat(verification.totalDebits())
            .isEqualTo(GbpAmount.ZERO);
        assertThat(verification.totalCredits())
            .isEqualTo(GbpAmount.ZERO);
        assertThat(verification.consistent())
            .isTrue();

        assertThat(
            queryService.findAccountEntries(accountId)
        )
            .isEmpty();
    }

    @Test
    void rejectsUnknownTransactionAndAccount() {
        UUID transactionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(
            () ->
                queryService.findTransaction(
                    transactionId
                )
        )
            .isInstanceOf(
                LedgerTransactionNotFoundException.class
            )
            .hasMessageContaining(
                transactionId.toString()
            );

        assertThatThrownBy(
            () ->
                queryService.findAccountEntries(
                    accountId
                )
        )
            .isInstanceOf(
                LedgerAccountNotFoundException.class
            )
            .hasMessageContaining(
                accountId.toString()
            );

        assertThatThrownBy(
            () ->
                queryService.verifyAccountBalance(
                    accountId
                )
        )
            .isInstanceOf(
                LedgerAccountNotFoundException.class
            );
    }

    private LedgerPostingCommand command(
        String reference,
        UUID debitAccountId,
        UUID creditAccountId,
        long minorUnits
    ) {
        return new LedgerPostingCommand(
            "INTERNAL_TRANSFER",
            reference,
            null,
            "Ledger query test posting",
            List.of(
                new LedgerPostingEntry(
                    debitAccountId,
                    LedgerEntrySide.DEBIT,
                    GbpAmount.ofMinorUnits(
                        minorUnits
                    ),
                    "Debit"
                ),
                new LedgerPostingEntry(
                    creditAccountId,
                    LedgerEntrySide.CREDIT,
                    GbpAmount.ofMinorUnits(
                        minorUnits
                    ),
                    "Credit"
                )
            )
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

    private void updateBalance(
        UUID accountId,
        long minorUnits
    ) {
        jdbcTemplate.update(
            """
            UPDATE customer_account
            SET
                balance_minor_units = ?,
                updated_at = ?
            WHERE id = ?
            """,
            minorUnits,
            ACCOUNT_TIME
                .plusSeconds(60)
                .atOffset(ZoneOffset.UTC),
            accountId
        );
    }
}