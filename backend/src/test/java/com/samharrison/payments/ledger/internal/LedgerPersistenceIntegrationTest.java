package com.samharrison.payments.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class LedgerPersistenceIntegrationTest {

    private static final Instant POSTED_AT =
        Instant.parse("2026-06-29T14:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_ledger_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private LedgerPersistenceStore store;

    @Autowired
    private LedgerTransactionRecordRepository
        transactionRepository;

    @Autowired
    private LedgerEntryRecordRepository
        entryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesLedgerSchemaMigration() {
        Long migrationCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '9'
                  AND success = TRUE
                """,
                Long.class
            );

        assertThat(migrationCount)
            .isEqualTo(1L);
    }

    @Test
    void persistsAndReloadsBalancedLedgerTransaction() {
        UUID debitAccountId =
            insertCustomerAccount(
                "Debit Customer"
            );

        UUID creditAccountId =
            insertCustomerAccount(
                "Credit Customer"
            );

        LedgerTransaction transaction =
            LedgerTransaction.post(
                LedgerTransactionType.of(
                    "INTERNAL_TRANSFER"
                ),
                "payment-1001",
                null,
                POSTED_AT,
                "Internal account transfer",
                List.of(
                    draft(
                        debitAccountId,
                        LedgerSide.DEBIT,
                        2_500L,
                        "Source account debit"
                    ),
                    draft(
                        creditAccountId,
                        LedgerSide.CREDIT,
                        2_500L,
                        "Destination account credit"
                    )
                )
            );

        store.save(transaction);
        entityManager.clear();

        LedgerTransactionRecord header =
            transactionRepository
                .findById(transaction.id())
                .orElseThrow();

        assertThat(header.transactionType())
            .isEqualTo("INTERNAL_TRANSFER");
        assertThat(header.businessReference())
            .isEqualTo("payment-1001");
        assertThat(header.correctsTransactionId())
            .isNull();
        assertThat(header.postedAt())
            .isEqualTo(POSTED_AT);
        assertThat(header.description())
            .isEqualTo(
                "Internal account transfer"
            );

        List<LedgerEntryRecord> entries =
            entryRepository
                .findAllByTransactionIdOrderBySequenceAsc(
                    transaction.id()
                );

        assertThat(entries)
            .hasSize(2);

        LedgerEntryRecord debit = entries.get(0);
        LedgerEntryRecord credit = entries.get(1);

        assertThat(debit.transactionId())
            .isEqualTo(transaction.id());
        assertThat(debit.ledgerAccountId())
            .isEqualTo(debitAccountId);
        assertThat(debit.side())
            .isEqualTo(LedgerSide.DEBIT);
        assertThat(debit.amount())
            .isEqualTo(
                GbpAmount.ofMinorUnits(2_500L)
            );
        assertThat(debit.currency())
            .isEqualTo(GbpAmount.CURRENCY_CODE);
        assertThat(debit.sequence())
            .isEqualTo(1);

        assertThat(credit.ledgerAccountId())
            .isEqualTo(creditAccountId);
        assertThat(credit.side())
            .isEqualTo(LedgerSide.CREDIT);
        assertThat(credit.amount())
            .isEqualTo(
                GbpAmount.ofMinorUnits(2_500L)
            );
        assertThat(credit.sequence())
            .isEqualTo(2);
    }

    @Test
    void persistsCompensatingTransactionLink() {
        UUID firstAccountId =
            insertCustomerAccount(
                "Correction Customer One"
            );

        UUID secondAccountId =
            insertCustomerAccount(
                "Correction Customer Two"
            );

        LedgerTransaction original =
            balancedTransaction(
                firstAccountId,
                secondAccountId,
                "payment-2001",
                null
            );

        store.save(original);

        LedgerTransaction correction =
            LedgerTransaction.post(
                LedgerTransactionType.of(
                    "CORRECTION"
                ),
                "correction-2001",
                original.id(),
                POSTED_AT.plusSeconds(60),
                "Compensating correction",
                List.of(
                    draft(
                        secondAccountId,
                        LedgerSide.DEBIT,
                        500L,
                        "Reverse destination credit"
                    ),
                    draft(
                        firstAccountId,
                        LedgerSide.CREDIT,
                        500L,
                        "Reverse source debit"
                    )
                )
            );

        store.save(correction);
        entityManager.clear();

        LedgerTransactionRecord reloaded =
            transactionRepository
                .findById(correction.id())
                .orElseThrow();

        assertThat(
            reloaded.correctsTransactionId()
        )
            .isEqualTo(original.id());
    }

    @Test
    void databaseRejectsUnknownLedgerAccount() {
        UUID transactionId =
            insertTransactionHeader(
                null
            );

        assertThatThrownBy(
            () ->
                insertLedgerEntry(
                    transactionId,
                    UUID.randomUUID(),
                    "DEBIT",
                    100L,
                    "GBP",
                    1,
                    "Missing account"
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsNonPositiveAmount() {
        UUID transactionId =
            insertTransactionHeader(
                null
            );

        UUID accountId =
            insertCustomerAccount(
                "Amount Constraint Customer"
            );

        assertThatThrownBy(
            () ->
                insertLedgerEntry(
                    transactionId,
                    accountId,
                    "DEBIT",
                    0L,
                    "GBP",
                    1,
                    "Zero amount"
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsUnknownSideAndCurrency() {
        UUID transactionId =
            insertTransactionHeader(
                null
            );

        UUID accountId =
            insertCustomerAccount(
                "Side Constraint Customer"
            );

        assertThatThrownBy(
            () ->
                insertLedgerEntry(
                    transactionId,
                    accountId,
                    "UNKNOWN",
                    100L,
                    "USD",
                    1,
                    "Unknown side and currency"
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsDuplicateEntrySequence() {
        UUID transactionId =
            insertTransactionHeader(
                null
            );

        UUID firstAccountId =
            insertCustomerAccount(
                "Sequence Customer One"
            );

        UUID secondAccountId =
            insertCustomerAccount(
                "Sequence Customer Two"
            );

        insertLedgerEntry(
            transactionId,
            firstAccountId,
            "DEBIT",
            100L,
            "GBP",
            1,
            "First entry"
        );

        assertThatThrownBy(
            () ->
                insertLedgerEntry(
                    transactionId,
                    secondAccountId,
                    "CREDIT",
                    100L,
                    "GBP",
                    1,
                    "Duplicate sequence"
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void databaseRejectsSelfCorrection() {
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(
            () ->
                insertTransactionHeader(
                    transactionId,
                    transactionId
                )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    private LedgerTransaction balancedTransaction(
        UUID debitAccountId,
        UUID creditAccountId,
        String reference,
        UUID correctsTransactionId
    ) {
        return LedgerTransaction.post(
            LedgerTransactionType.of(
                "INTERNAL_TRANSFER"
            ),
            reference,
            correctsTransactionId,
            POSTED_AT,
            "Balanced persistence transaction",
            List.of(
                draft(
                    debitAccountId,
                    LedgerSide.DEBIT,
                    500L,
                    "Debit"
                ),
                draft(
                    creditAccountId,
                    LedgerSide.CREDIT,
                    500L,
                    "Credit"
                )
            )
        );
    }

    private LedgerEntryDraft draft(
        UUID accountId,
        LedgerSide side,
        long minorUnits,
        String description
    ) {
        return new LedgerEntryDraft(
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

    private UUID insertTransactionHeader(
        UUID correctsTransactionId
    ) {
        UUID transactionId = UUID.randomUUID();

        insertTransactionHeader(
            transactionId,
            correctsTransactionId
        );

        return transactionId;
    }

    private void insertTransactionHeader(
        UUID transactionId,
        UUID correctsTransactionId
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
            correctsTransactionId,
            POSTED_AT.atOffset(ZoneOffset.UTC),
            "Persistence constraint test"
        );
    }

    private void insertLedgerEntry(
        UUID transactionId,
        UUID accountId,
        String side,
        long amountMinorUnits,
        String currency,
        int sequence,
        String description
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
            currency,
            sequence,
            description
        );
    }
}