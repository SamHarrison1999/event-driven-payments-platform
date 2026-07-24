package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementCsvParserTest {

    private static final String HEADER =
        "settlement_record_id,payment_id,"
            + "amount_minor_units,currency,settled_at";

    private static final String PAYMENT_ID =
        "00000000-0000-0000-0000-000000000001";

    private static final String VALID_CSV =
        """
        settlement_record_id,payment_id,amount_minor_units,currency,settled_at
        "record-1","00000000-0000-0000-0000-000000000001","1234","GBP","2026-07-24T10:00:00Z"
        record-2,00000000-0000-0000-0000-000000000002,5678,GBP,2026-07-24T10:01:00.123456Z
        """
            .strip();

    private final SettlementCsvParser parser =
        new SettlementCsvParser();

    @Test
    void parsesQuotedAndUnquotedRowsAndFingerprintsRawBytes() {
        byte[] rawBytes =
            VALID_CSV.getBytes(StandardCharsets.UTF_8);

        ParsedSettlementFile parsed =
            parser.parse(rawBytes);

        assertThat(parsed.rawFileSha256())
            .isEqualTo(
                "12ab016179e0aae2e0b249ba0b93333d4"
                    + "a9db31a71601d940e3d07d67e0da332"
            );
        assertThat(parsed.rawFileSizeBytes())
            .isEqualTo(239);
        assertThat(parsed.records())
            .containsExactly(
                new ParsedSettlementRecord(
                    1,
                    "record-1",
                    UUID.fromString(PAYMENT_ID),
                    1_234L,
                    "GBP",
                    Instant.parse(
                        "2026-07-24T10:00:00Z"
                    )
                ),
                new ParsedSettlementRecord(
                    2,
                    "record-2",
                    UUID.fromString(
                        "00000000-0000-0000-0000-"
                            + "000000000002"
                    ),
                    5_678L,
                    "GBP",
                    Instant.parse(
                        "2026-07-24T10:01:00.123456Z"
                    )
                )
            );
    }

    @Test
    void treatsCrLfBytesAsASeparateIdempotencyIdentity() {
        ParsedSettlementFile lf =
            parser.parse(
                VALID_CSV.getBytes(
                    StandardCharsets.UTF_8
                )
            );
        ParsedSettlementFile crlf =
            parser.parse(
                VALID_CSV
                    .replace("\n", "\r\n")
                    .getBytes(StandardCharsets.UTF_8)
            );

        assertThat(crlf.records())
            .isEqualTo(lf.records());
        assertThat(crlf.rawFileSha256())
            .isNotEqualTo(lf.rawFileSha256());
    }

    @Test
    void rejectsAnEmptyFile() {
        assertInvalid(
            new byte[0],
            SettlementFileErrorCode.EMPTY_FILE,
            null
        );
    }

    @Test
    void rejectsAFileOverOneMebibyte() {
        byte[] oversized =
            new byte[
                SettlementCsvParser.MAX_FILE_SIZE_BYTES
                    + 1
            ];

        java.util.Arrays.fill(
            oversized,
            (byte) 'a'
        );

        assertInvalid(
            oversized,
            SettlementFileErrorCode.FILE_TOO_LARGE,
            null
        );
    }

    @Test
    void rejectsUtf8Bom() {
        byte[] content =
            VALID_CSV.getBytes(StandardCharsets.UTF_8);
        byte[] withBom =
            new byte[content.length + 3];

        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(
            content,
            0,
            withBom,
            3,
            content.length
        );

        assertInvalid(
            withBom,
            SettlementFileErrorCode
                .UTF8_BOM_NOT_ALLOWED,
            null
        );
    }

    @Test
    void rejectsMalformedUtf8() {
        assertInvalid(
            new byte[] {
                (byte) 0xC3,
                (byte) 0x28
            },
            SettlementFileErrorCode.MALFORMED_UTF8,
            null
        );
    }

    @Test
    void rejectsNulBytesBeforeCsvParsing() {
        byte[] rawBytes =
            VALID_CSV.getBytes(StandardCharsets.UTF_8);
        rawBytes[rawBytes.length - 1] = 0;

        assertInvalid(
            rawBytes,
            SettlementFileErrorCode.NUL_CHARACTER,
            null
        );
    }

    @Test
    void rejectsMissingReorderedOrExtraHeaderColumns() {
        assertInvalidText(
            "payment_id,settlement_record_id,"
                + "amount_minor_units,currency,settled_at"
                + System.lineSeparator()
                + validRow("record-1"),
            SettlementFileErrorCode.INVALID_HEADER,
            null
        );

        assertInvalidText(
            "settlement_record_id,payment_id,"
                + "amount_minor_units,currency"
                + System.lineSeparator()
                + validRow("record-1"),
            SettlementFileErrorCode.INVALID_HEADER,
            null
        );

        assertInvalidText(
            HEADER
                + ",extra"
                + System.lineSeparator()
                + validRow("record-1"),
            SettlementFileErrorCode.INVALID_HEADER,
            null
        );
    }

    @Test
    void rejectsMissingOrExtraDataColumns() {
        assertInvalidText(
            HEADER
                + "\nrecord-1,"
                + PAYMENT_ID
                + ",100,GBP",
            SettlementFileErrorCode
                .INVALID_COLUMN_COUNT,
            1
        );

        assertInvalidText(
            HEADER
                + "\n"
                + validRow("record-1")
                + ",extra",
            SettlementFileErrorCode
                .INVALID_COLUMN_COUNT,
            1
        );
    }

    @Test
    void rejectsFilesWithoutDataRows() {
        assertInvalidText(
            HEADER,
            SettlementFileErrorCode.EMPTY_DATA,
            null
        );
    }

    @Test
    void rejectsBlankRecords() {
        assertInvalidText(
            HEADER
                + "\n"
                + validRow("record-1")
                + "\n\n"
                + validRow("record-2"),
            SettlementFileErrorCode
                .INVALID_COLUMN_COUNT,
            2
        );
    }

    @Test
    void rejectsMoreThanOneThousandRows() {
        StringBuilder content =
            new StringBuilder(HEADER);

        for (
            int index = 1;
            index <= 1_001;
            index++
        ) {
            content
                .append('\n')
                .append(
                    validRow("record-" + index)
                );
        }

        assertInvalidText(
            content.toString(),
            SettlementFileErrorCode.TOO_MANY_ROWS,
            1_001
        );
    }

    @Test
    void rejectsDuplicateExternalIdentifiers() {
        assertInvalidText(
            HEADER
                + "\n"
                + validRow("record-1")
                + "\n"
                + validRow("record-1"),
            SettlementFileErrorCode
                .DUPLICATE_SETTLEMENT_RECORD_ID,
            2
        );
    }

    @Test
    void rejectsUnsafeExternalIdentifiers() {
        assertInvalidText(
            HEADER
                + "\n"
                + validRow("unsafe identifier"),
            SettlementFileErrorCode
                .INVALID_SETTLEMENT_RECORD_ID,
            1
        );
    }

    @Test
    void rejectsNonCanonicalPaymentIdentifiers() {
        assertInvalidText(
            HEADER
                + "\nrecord-1,"
                + "AAAAAAAA-AAAA-AAAA-AAAA-"
                + "AAAAAAAAAAAA"
                + ",100,GBP,2026-07-24T10:00:00Z",
            SettlementFileErrorCode
                .INVALID_PAYMENT_ID,
            1
        );
    }

    @Test
    void rejectsInvalidAndOverflowingAmounts() {
        assertInvalidText(
            rowWithAmount("0"),
            SettlementFileErrorCode.INVALID_AMOUNT,
            1
        );
        assertInvalidText(
            rowWithAmount("01"),
            SettlementFileErrorCode.INVALID_AMOUNT,
            1
        );
        assertInvalidText(
            rowWithAmount("9223372036854775808"),
            SettlementFileErrorCode.INVALID_AMOUNT,
            1
        );
    }

    @Test
    void rejectsNonGbpCurrency() {
        assertInvalidText(
            HEADER
                + "\nrecord-1,"
                + PAYMENT_ID
                + ",100,USD,2026-07-24T10:00:00Z",
            SettlementFileErrorCode
                .INVALID_CURRENCY,
            1
        );
    }

    @Test
    void rejectsOffsetsAndMalformedInstants() {
        assertInvalidText(
            rowWithSettledAt(
                "2026-07-24T11:00:00+01:00"
            ),
            SettlementFileErrorCode
                .INVALID_SETTLED_AT,
            1
        );
        assertInvalidText(
            rowWithSettledAt(
                "2026-07-24T10:00:99Z"
            ),
            SettlementFileErrorCode
                .INVALID_SETTLED_AT,
            1
        );
    }

    @Test
    void rejectsEmbeddedLineBreaksAndOtherControlCharacters() {
        assertInvalidText(
            HEADER
                + "\n\"record\none\","
                + PAYMENT_ID
                + ",100,GBP,2026-07-24T10:00:00Z",
            SettlementFileErrorCode
                .CONTROL_CHARACTER,
            1
        );

        assertInvalidText(
            HEADER
                + "\n\"record\tone\","
                + PAYMENT_ID
                + ",100,GBP,2026-07-24T10:00:00Z",
            SettlementFileErrorCode
                .CONTROL_CHARACTER,
            1
        );
    }

    @Test
    void rejectsMalformedCsvQuoting() {
        assertInvalidText(
            HEADER
                + "\n\"record-1,"
                + PAYMENT_ID
                + ",100,GBP,2026-07-24T10:00:00Z",
            SettlementFileErrorCode.INVALID_CSV,
            null
        );
    }

    private void assertInvalidText(
        String content,
        SettlementFileErrorCode code,
        Integer rowNumber
    ) {
        assertInvalid(
            content.getBytes(StandardCharsets.UTF_8),
            code,
            rowNumber
        );
    }

    private void assertInvalid(
        byte[] rawBytes,
        SettlementFileErrorCode code,
        Integer rowNumber
    ) {
        assertThatThrownBy(
            () -> parser.parse(rawBytes)
        )
            .isInstanceOfSatisfying(
                InvalidSettlementFileException.class,
                exception -> {
                    assertThat(exception.code())
                        .isEqualTo(code);
                    assertThat(exception.rowNumber())
                        .isEqualTo(rowNumber);
                }
            );
    }

    private static String validRow(
        String externalIdentifier
    ) {
        return externalIdentifier
            + ","
            + PAYMENT_ID
            + ",100,GBP,2026-07-24T10:00:00Z";
    }

    private static String rowWithAmount(
        String amount
    ) {
        return HEADER
            + "\nrecord-1,"
            + PAYMENT_ID
            + ","
            + amount
            + ",GBP,2026-07-24T10:00:00Z";
    }

    private static String rowWithSettledAt(
        String settledAt
    ) {
        return HEADER
            + "\nrecord-1,"
            + PAYMENT_ID
            + ",100,GBP,"
            + settledAt;
    }
}
