package com.samharrison.payments.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerTransactionTest {

    private static final Instant POSTED_AT =
        Instant.parse("2026-06-29T12:00:00Z");

    @Test
    void createsBalancedTransactionWithOrderedEntries() {
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        UUID correctedTransactionId =
            UUID.randomUUID();

        List<LedgerEntryDraft> drafts =
            new ArrayList<>(
                List.of(
                    draft(
                        debitAccountId,
                        LedgerSide.DEBIT,
                        1_250L,
                        "Customer debit"
                    ),
                    draft(
                        creditAccountId,
                        LedgerSide.CREDIT,
                        1_250L,
                        "Settlement credit"
                    )
                )
            );

        LedgerTransaction transaction =
            LedgerTransaction.post(
                LedgerTransactionType.of(
                    " correction "
                ),
                " payment-123 ",
                correctedTransactionId,
                POSTED_AT,
                " Customer payment ",
                drafts
            );

        drafts.clear();

        assertThat(transaction.id()).isNotNull();
        assertThat(transaction.type().value())
            .isEqualTo("CORRECTION");
        assertThat(transaction.reference())
            .isEqualTo("payment-123");
        assertThat(
            transaction.correctsTransactionId()
        )
            .isEqualTo(correctedTransactionId);
        assertThat(transaction.postedAt())
            .isEqualTo(POSTED_AT);
        assertThat(transaction.description())
            .isEqualTo("Customer payment");

        assertThat(transaction.entries())
            .hasSize(2);

        LedgerEntry debit =
            transaction.entries().get(0);
        LedgerEntry credit =
            transaction.entries().get(1);

        assertThat(debit.id())
            .isNotEqualTo(credit.id());

        assertThat(debit.transactionId())
            .isEqualTo(transaction.id());
        assertThat(credit.transactionId())
            .isEqualTo(transaction.id());

        assertThat(debit.ledgerAccountId())
            .isEqualTo(debitAccountId);
        assertThat(credit.ledgerAccountId())
            .isEqualTo(creditAccountId);

        assertThat(debit.side())
            .isEqualTo(LedgerSide.DEBIT);
        assertThat(credit.side())
            .isEqualTo(LedgerSide.CREDIT);

        assertThat(debit.sequence()).isEqualTo(1);
        assertThat(credit.sequence()).isEqualTo(2);

        assertThatThrownBy(
            () -> transaction.entries().clear()
        )
            .isInstanceOf(
                UnsupportedOperationException.class
            );
    }

    @Test
    void acceptsBalancedSplitEntries() {
        LedgerTransaction transaction =
            LedgerTransaction.post(
                LedgerTransactionType.of(
                    "ADJUSTMENT"
                ),
                null,
                null,
                POSTED_AT,
                "Balanced split",
                List.of(
                    draft(
                        UUID.randomUUID(),
                        LedgerSide.DEBIT,
                        1_000L,
                        "Debit"
                    ),
                    draft(
                        UUID.randomUUID(),
                        LedgerSide.CREDIT,
                        600L,
                        "First credit"
                    ),
                    draft(
                        UUID.randomUUID(),
                        LedgerSide.CREDIT,
                        400L,
                        "Second credit"
                    )
                )
            );

        assertThat(transaction.reference()).isNull();
        assertThat(transaction.entries())
            .extracting(LedgerEntry::sequence)
            .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsTooFewEntries() {
        assertThatThrownBy(
            () ->
                LedgerTransaction.post(
                    LedgerTransactionType.of(
                        "PAYMENT"
                    ),
                    null,
                    null,
                    POSTED_AT,
                    "Invalid journal",
                    List.of(
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            100L,
                            "Debit"
                        )
                    )
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            )
            .hasMessageContaining(
                "at least two entries"
            );
    }

    @Test
    void rejectsOneSidedEntries() {
        assertThatThrownBy(
            () ->
                LedgerTransaction.post(
                    LedgerTransactionType.of(
                        "PAYMENT"
                    ),
                    null,
                    null,
                    POSTED_AT,
                    "Invalid journal",
                    List.of(
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            100L,
                            "First debit"
                        ),
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            100L,
                            "Second debit"
                        )
                    )
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            )
            .hasMessageContaining(
                "at least one debit and one credit"
            );
    }

    @Test
    void rejectsUnbalancedEntries() {
        assertThatThrownBy(
            () ->
                LedgerTransaction.post(
                    LedgerTransactionType.of(
                        "PAYMENT"
                    ),
                    null,
                    null,
                    POSTED_AT,
                    "Invalid journal",
                    List.of(
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            100L,
                            "Debit"
                        ),
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.CREDIT,
                            99L,
                            "Credit"
                        )
                    )
                )
        )
            .isInstanceOf(
                UnbalancedLedgerTransactionException.class
            )
            .satisfies(
                exception -> {
                    UnbalancedLedgerTransactionException
                        unbalanced =
                            (
                                UnbalancedLedgerTransactionException
                            ) exception;

                    assertThat(
                        unbalanced.debitMinorUnits()
                    )
                        .isEqualTo(100L);
                    assertThat(
                        unbalanced.creditMinorUnits()
                    )
                        .isEqualTo(99L);
                }
            );
    }

    @Test
    void rejectsZeroEntryAmount() {
        assertThatThrownBy(
            () ->
                draft(
                    UUID.randomUUID(),
                    LedgerSide.DEBIT,
                    0L,
                    "Zero debit"
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            )
            .hasMessageContaining(
                "greater than zero"
            );
    }

    @Test
    void rejectsInvalidTypeAndDescriptions() {
        assertThatThrownBy(
            () ->
                LedgerTransactionType.of(
                    "invalid-type"
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            );

        assertThatThrownBy(
            () ->
                draft(
                    UUID.randomUUID(),
                    LedgerSide.DEBIT,
                    100L,
                    "   "
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            );

        assertThatThrownBy(
            () ->
                LedgerTransaction.post(
                    LedgerTransactionType.of(
                        "PAYMENT"
                    ),
                    null,
                    null,
                    POSTED_AT,
                    "line one\nline two",
                    List.of(
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            100L,
                            "Debit"
                        ),
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.CREDIT,
                            100L,
                            "Credit"
                        )
                    )
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            )
            .hasMessageContaining(
                "control characters"
            );
    }

    @Test
    void rejectsLedgerTotalOverflow() {
        assertThatThrownBy(
            () ->
                LedgerTransaction.post(
                    LedgerTransactionType.of(
                        "ADJUSTMENT"
                    ),
                    null,
                    null,
                    POSTED_AT,
                    "Overflowing journal",
                    List.of(
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            Long.MAX_VALUE,
                            "Large debit"
                        ),
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.DEBIT,
                            1L,
                            "Overflow debit"
                        ),
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.CREDIT,
                            Long.MAX_VALUE,
                            "Large credit"
                        ),
                        draft(
                            UUID.randomUUID(),
                            LedgerSide.CREDIT,
                            1L,
                            "Overflow credit"
                        )
                    )
                )
        )
            .isInstanceOf(
                InvalidLedgerTransactionException.class
            )
            .hasMessageContaining(
                "supported range"
            )
            .hasCauseInstanceOf(
                ArithmeticException.class
            );
    }

    private static LedgerEntryDraft draft(
        UUID ledgerAccountId,
        LedgerSide side,
        long minorUnits,
        String description
    ) {
        return new LedgerEntryDraft(
            ledgerAccountId,
            side,
            GbpAmount.ofMinorUnits(minorUnits),
            description
        );
    }
}