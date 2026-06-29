package com.samharrison.payments.ledger.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LedgerTransaction {

    public static final int MAX_REFERENCE_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private final UUID id;
    private final LedgerTransactionType type;
    private final String reference;
    private final UUID correctsTransactionId;
    private final Instant postedAt;
    private final String description;
    private final List<LedgerEntry> entries;

    private LedgerTransaction(
        UUID id,
        LedgerTransactionType type,
        String reference,
        UUID correctsTransactionId,
        Instant postedAt,
        String description,
        List<LedgerEntry> entries
    ) {
        this.id = id;
        this.type = type;
        this.reference = reference;
        this.correctsTransactionId =
            correctsTransactionId;
        this.postedAt = postedAt;
        this.description = description;
        this.entries = entries;
    }

    public static LedgerTransaction post(
        LedgerTransactionType type,
        String reference,
        UUID correctsTransactionId,
        Instant postedAt,
        String description,
        List<LedgerEntryDraft> entryDrafts
    ) {
        LedgerTransactionType requiredType =
            Objects.requireNonNull(
                type,
                "type must not be null"
            );

        Instant requiredPostedAt =
            Objects.requireNonNull(
                postedAt,
                "postedAt must not be null"
            );

        String normalizedReference =
            normalizeOptionalText(
                reference,
                "Ledger transaction reference",
                MAX_REFERENCE_LENGTH
            );

        String normalizedDescription =
            normalizeRequiredText(
                description,
                "Ledger transaction description",
                MAX_DESCRIPTION_LENGTH
            );

        List<LedgerEntryDraft> drafts =
            requireDrafts(entryDrafts);

        verifyBalance(drafts);

        UUID transactionId = UUID.randomUUID();
        List<LedgerEntry> createdEntries =
            new ArrayList<>(drafts.size());

        for (int index = 0; index < drafts.size(); index++) {
            createdEntries.add(
                LedgerEntry.from(
                    transactionId,
                    index + 1,
                    drafts.get(index)
                )
            );
        }

        return new LedgerTransaction(
            transactionId,
            requiredType,
            normalizedReference,
            correctsTransactionId,
            requiredPostedAt,
            normalizedDescription,
            List.copyOf(createdEntries)
        );
    }

    private static List<LedgerEntryDraft> requireDrafts(
        List<LedgerEntryDraft> entryDrafts
    ) {
        if (entryDrafts == null) {
            throw new InvalidLedgerTransactionException(
                "Ledger transaction entries are required."
            );
        }

        if (entryDrafts.size() < 2) {
            throw new InvalidLedgerTransactionException(
                "A ledger transaction must contain "
                    + "at least two entries."
            );
        }

        List<LedgerEntryDraft> copy =
            new ArrayList<>(entryDrafts.size());

        for (LedgerEntryDraft draft : entryDrafts) {
            if (draft == null) {
                throw new InvalidLedgerTransactionException(
                    "Ledger transaction entries must "
                        + "not contain null values."
                );
            }

            copy.add(draft);
        }

        return List.copyOf(copy);
    }

    private static void verifyBalance(
        List<LedgerEntryDraft> drafts
    ) {
        long debitMinorUnits = 0L;
        long creditMinorUnits = 0L;
        boolean hasDebit = false;
        boolean hasCredit = false;

        try {
            for (LedgerEntryDraft draft : drafts) {
                long amount =
                    draft.amount().minorUnits();

                if (draft.side() == LedgerSide.DEBIT) {
                    hasDebit = true;
                    debitMinorUnits =
                        Math.addExact(
                            debitMinorUnits,
                            amount
                        );
                } else {
                    hasCredit = true;
                    creditMinorUnits =
                        Math.addExact(
                            creditMinorUnits,
                            amount
                        );
                }
            }
        } catch (ArithmeticException exception) {
            throw new InvalidLedgerTransactionException(
                "Ledger transaction totals exceed "
                    + "the supported range.",
                exception
            );
        }

        if (!hasDebit || !hasCredit) {
            throw new InvalidLedgerTransactionException(
                "A ledger transaction must contain "
                    + "at least one debit and one credit."
            );
        }

        if (debitMinorUnits != creditMinorUnits) {
            throw new UnbalancedLedgerTransactionException(
                debitMinorUnits,
                creditMinorUnits
            );
        }
    }

    private static String normalizeRequiredText(
        String rawValue,
        String fieldName,
        int maximumLength
    ) {
        if (rawValue == null) {
            throw new InvalidLedgerTransactionException(
                fieldName + " is required."
            );
        }

        String normalized = rawValue.strip();

        if (normalized.isEmpty()) {
            throw new InvalidLedgerTransactionException(
                fieldName + " is required."
            );
        }

        validateText(
            normalized,
            fieldName,
            maximumLength
        );

        return normalized;
    }

    private static String normalizeOptionalText(
        String rawValue,
        String fieldName,
        int maximumLength
    ) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.strip();

        if (normalized.isEmpty()) {
            return null;
        }

        validateText(
            normalized,
            fieldName,
            maximumLength
        );

        return normalized;
    }

    private static void validateText(
        String value,
        String fieldName,
        int maximumLength
    ) {
        if (value.length() > maximumLength) {
            throw new InvalidLedgerTransactionException(
                fieldName
                    + " must not exceed "
                    + maximumLength
                    + " characters."
            );
        }

        boolean containsControlCharacter =
            value
                .codePoints()
                .anyMatch(Character::isISOControl);

        if (containsControlCharacter) {
            throw new InvalidLedgerTransactionException(
                fieldName
                    + " must not contain control "
                    + "characters."
            );
        }
    }

    public UUID id() {
        return id;
    }

    public LedgerTransactionType type() {
        return type;
    }

    public String reference() {
        return reference;
    }

    public UUID correctsTransactionId() {
        return correctsTransactionId;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public String description() {
        return description;
    }

    public List<LedgerEntry> entries() {
        return entries;
    }
}