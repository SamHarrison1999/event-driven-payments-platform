package com.samharrison.payments.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.samharrison.payments.ledger.LedgerEntrySide;
import com.samharrison.payments.ledger.LedgerPostingCommand;
import com.samharrison.payments.ledger.LedgerPostingEntry;
import com.samharrison.payments.ledger.LedgerPostingService;
import com.samharrison.payments.ledger.PostedLedgerTransaction;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class LedgerDatabaseInvariantIntegrationTest {

    private static final Instant POSTED_AT =
        Instant.parse("2026-06-29T16:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_ledger_invariant_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private LedgerPostingService postingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager
        transactionManager;

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
    void appliesLedgerInvariantMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '10'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void commitsBalancedPostingThroughDeferredCheck() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Balanced Customer One"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Balanced Customer Two"
            );

        PostedLedgerTransaction posted =
            postingService.post(
                command(
                    debitAccountId,
                    creditAccountId,
                    1_200L
                )
            );

        assertThat(posted.entries())
            .hasSize(2);

        assertThat(
            countRows("ledger_transaction")
        )
            .isEqualTo(1L);

        assertThat(
            countRows("ledger_entry")
        )
            .isEqualTo(2L);
    }

    @Test
    void rejectsHeaderWithoutEntriesAtCommit() {
        UUID transactionId = UUID.randomUUID();

        Throwable thrown =
            catchThrowable(
                () ->
                    executeInTransaction(
                        () ->
                            insertTransactionHeader(
                                transactionId
                            )
                    )
            );

        assertDeferredInvariantFailure(
            thrown,
            "at least two entries"
        );

        assertThat(
            countRows("ledger_transaction")
        )
            .isZero();
    }

    @Test
    void rejectsOneSidedTransactionAtCommit() {
        UUID firstAccountId =
            insertCustomerAccount(
                "One Sided Customer One"
            );

        UUID secondAccountId =
            insertCustomerAccount(
                "One Sided Customer Two"
            );

        UUID transactionId = UUID.randomUUID();

        Throwable thrown =
            catchThrowable(
                () ->
                    executeInTransaction(
                        () -> {
                            insertTransactionHeader(
                                transactionId
                            );

                            insertLedgerEntry(
                                transactionId,
                                firstAccountId,
                                "DEBIT",
                                500L,
                                1
                            );

                            insertLedgerEntry(
                                transactionId,
                                secondAccountId,
                                "DEBIT",
                                500L,
                                2
                            );
                        }
                    )
            );

        assertDeferredInvariantFailure(
            thrown,
            "at least one debit and one credit"
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
    void rejectsUnbalancedTransactionAtCommit() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Unbalanced Customer One"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Unbalanced Customer Two"
            );

        UUID transactionId = UUID.randomUUID();

        Throwable thrown =
            catchThrowable(
                () ->
                    executeInTransaction(
                        () -> {
                            insertTransactionHeader(
                                transactionId
                            );

                            insertLedgerEntry(
                                transactionId,
                                debitAccountId,
                                "DEBIT",
                                500L,
                                1
                            );

                            insertLedgerEntry(
                                transactionId,
                                creditAccountId,
                                "CREDIT",
                                499L,
                                2
                            );
                        }
                    )
            );

        assertDeferredInvariantFailure(
            thrown,
            "is unbalanced"
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
    void rejectsUpdatesAndDeletesAfterPosting() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Immutable Customer One"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Immutable Customer Two"
            );

        PostedLedgerTransaction posted =
            postingService.post(
                command(
                    debitAccountId,
                    creditAccountId,
                    900L
                )
            );

        assertImmutableMutationRejected(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE ledger_entry
                    SET description = ?
                    WHERE transaction_id = ?
                    """,
                    "Mutated entry",
                    posted.id()
                )
        );

        assertImmutableMutationRejected(
            () ->
                jdbcTemplate.update(
                    """
                    DELETE FROM ledger_entry
                    WHERE transaction_id = ?
                    """,
                    posted.id()
                )
        );

        assertImmutableMutationRejected(
            () ->
                jdbcTemplate.update(
                    """
                    UPDATE ledger_transaction
                    SET description = ?
                    WHERE id = ?
                    """,
                    "Mutated transaction",
                    posted.id()
                )
        );

        assertImmutableMutationRejected(
            () ->
                jdbcTemplate.update(
                    """
                    DELETE FROM ledger_transaction
                    WHERE id = ?
                    """,
                    posted.id()
                )
        );

        assertThat(
            countRows("ledger_transaction")
        )
            .isEqualTo(1L);

        assertThat(
            countRows("ledger_entry")
        )
            .isEqualTo(2L);
    }

    private LedgerPostingCommand command(
        UUID debitAccountId,
        UUID creditAccountId,
        long minorUnits
    ) {
        return new LedgerPostingCommand(
            "INTERNAL_TRANSFER",
            "invariant-test",
            null,
            "Invariant test posting",
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

    private void executeInTransaction(
        Runnable work
    ) {
        TransactionTemplate transaction =
            new TransactionTemplate(
                transactionManager
            );

        transaction.executeWithoutResult(
            ignored -> work.run()
        );
    }

    private static void
    assertDeferredInvariantFailure(
        Throwable thrown,
        String expectedMessage
    ) {
        assertThat(thrown)
            .isNotNull()
            .isInstanceOfAny(
                DataAccessException.class,
                TransactionSystemException.class
            )
            .hasStackTraceContaining(
                expectedMessage
            );
    }

    private static void
    assertImmutableMutationRejected(
        Runnable mutation
    ) {
        assertThatThrownBy(mutation::run)
            .isInstanceOf(
                DataAccessException.class
            )
            .hasStackTraceContaining(
                "Posted ledger records are immutable"
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
            POSTED_AT.atOffset(ZoneOffset.UTC),
            POSTED_AT.atOffset(ZoneOffset.UTC),
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
            POSTED_AT.atOffset(ZoneOffset.UTC),
            POSTED_AT.atOffset(ZoneOffset.UTC),
            0L
        );

        return accountId;
    }

    private void insertTransactionHeader(
        UUID transactionId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ledger_transaction (
                id,
                transaction_type,
                business_reference,
                corrects_transaction_id,
                posted_at,
                description
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            transactionId,
            "INTERNAL_TRANSFER",
            null,
            null,
            POSTED_AT.atOffset(ZoneOffset.UTC),
            "Database invariant test"
        );
    }

    private void insertLedgerEntry(
        UUID transactionId,
        UUID accountId,
        String side,
        long amountMinorUnits,
        int sequence
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ledger_entry (
                id,
                transaction_id,
                ledger_account_id,
                side,
                amount_minor_units,
                currency,
                entry_sequence,
                description
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            transactionId,
            accountId,
            side,
            amountMinorUnits,
            "GBP",
            sequence,
            "Database invariant entry"
        );
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