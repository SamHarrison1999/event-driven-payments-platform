package com.samharrison.payments.ledger;

import com.samharrison.payments.ledger.internal.InvalidLedgerTransactionException;
import com.samharrison.payments.ledger.internal.LedgerEntry;
import com.samharrison.payments.ledger.internal.LedgerEntryDraft;
import com.samharrison.payments.ledger.internal.LedgerPersistenceStore;
import com.samharrison.payments.ledger.internal.LedgerSide;
import com.samharrison.payments.ledger.internal.LedgerTransaction;
import com.samharrison.payments.ledger.internal.LedgerTransactionType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerPostingService {

    private final LedgerPersistenceStore store;
    private final Clock clock;

    public LedgerPostingService(
        LedgerPersistenceStore store,
        Clock clock
    ) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public PostedLedgerTransaction post(
        LedgerPostingCommand command
    ) {
        LedgerPostingCommand required =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        try {
            LedgerTransaction transaction =
                LedgerTransaction.post(
                    LedgerTransactionType.of(
                        required.transactionType()
                    ),
                    required.businessReference(),
                    required.correctsTransactionId(),
                    Instant.now(clock),
                    required.description(),
                    toInternalEntries(
                        required.entries()
                    )
                );

            store.save(transaction);

            return toPostedTransaction(transaction);
        } catch (
            InvalidLedgerTransactionException exception
        ) {
            throw new InvalidLedgerPostingException(
                exception.getMessage(),
                exception
            );
        }
    }

    private static List<LedgerEntryDraft>
    toInternalEntries(
        List<LedgerPostingEntry> entries
    ) {
        return entries
            .stream()
            .map(
                entry ->
                    new LedgerEntryDraft(
                        entry.ledgerAccountId(),
                        toInternalSide(entry.side()),
                        entry.amount(),
                        entry.description()
                    )
            )
            .toList();
    }

    private static LedgerSide toInternalSide(
        LedgerEntrySide side
    ) {
        return switch (side) {
            case DEBIT -> LedgerSide.DEBIT;
            case CREDIT -> LedgerSide.CREDIT;
        };
    }

    private static LedgerEntrySide toPublicSide(
        LedgerSide side
    ) {
        return switch (side) {
            case DEBIT -> LedgerEntrySide.DEBIT;
            case CREDIT -> LedgerEntrySide.CREDIT;
        };
    }

    private static PostedLedgerTransaction
    toPostedTransaction(
        LedgerTransaction transaction
    ) {
        List<PostedLedgerEntry> entries =
            transaction
                .entries()
                .stream()
                .map(
                    LedgerPostingService::toPostedEntry
                )
                .toList();

        return new PostedLedgerTransaction(
            transaction.id(),
            transaction.type().value(),
            transaction.reference(),
            transaction.correctsTransactionId(),
            transaction.postedAt(),
            transaction.description(),
            entries
        );
    }

    private static PostedLedgerEntry toPostedEntry(
        LedgerEntry entry
    ) {
        return new PostedLedgerEntry(
            entry.id(),
            entry.ledgerAccountId(),
            toPublicSide(entry.side()),
            entry.amount(),
            entry.sequence(),
            entry.description()
        );
    }
}