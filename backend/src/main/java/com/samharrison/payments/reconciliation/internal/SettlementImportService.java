package com.samharrison.payments.reconciliation.internal;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
class SettlementImportService {

    private final SettlementCsvParser parser;

    private final SettlementImportTransaction transaction;

    SettlementImportService(
        SettlementCsvParser parser,
        SettlementImportTransaction transaction
    ) {
        this.parser =
            Objects.requireNonNull(
                parser,
                "parser must not be null"
            );
        this.transaction =
            Objects.requireNonNull(
                transaction,
                "transaction must not be null"
            );
    }

    @PreAuthorize(
        "hasAnyRole('RECONCILIATION_ANALYST', 'ADMIN')"
    )
    SettlementImportResponse importFile(
        UUID actorIdentityUserId,
        String originalFilename,
        byte[] rawFileBytes
    ) {
        ParsedSettlementFile parsedFile =
            parser.parse(rawFileBytes);

        return transaction.importFile(
            parsedFile,
            originalFilename,
            actorIdentityUserId
        );
    }
}
