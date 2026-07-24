package com.samharrison.payments.reconciliation.internal;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
class SettlementCsvParser {

    static final int MAX_FILE_SIZE_BYTES = 1_048_576;
    static final int MAX_DATA_ROWS = 1_000;

    private static final List<String> HEADER =
        List.of(
            "settlement_record_id",
            "payment_id",
            "amount_minor_units",
            "currency",
            "settled_at"
        );

    private static final Pattern SETTLEMENT_RECORD_ID =
        Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"
        );

    private static final Pattern AMOUNT =
        Pattern.compile("[1-9][0-9]{0,18}");

    private static final CSVFormat FORMAT =
        CSVFormat.RFC4180
            .builder()
            .setIgnoreEmptyLines(false)
            .setAllowMissingColumnNames(false)
            .setTrailingDelimiter(false)
            .get();

    ParsedSettlementFile parse(
        byte[] rawFileBytes
    ) {
        byte[] requiredBytes =
            java.util.Objects.requireNonNull(
                rawFileBytes,
                "rawFileBytes must not be null"
            );

        validateRawBytes(requiredBytes);

        String content = decodeUtf8(requiredBytes);
        String fingerprint = sha256(requiredBytes);

        List<ParsedSettlementRecord> records =
            parseRecords(content);

        return new ParsedSettlementFile(
            fingerprint,
            requiredBytes.length,
            records
        );
    }

    private static void validateRawBytes(
        byte[] rawFileBytes
    ) {
        if (rawFileBytes.length == 0) {
            throw invalid(
                SettlementFileErrorCode.EMPTY_FILE,
                "The settlement file is empty."
            );
        }

        if (rawFileBytes.length > MAX_FILE_SIZE_BYTES) {
            throw invalid(
                SettlementFileErrorCode.FILE_TOO_LARGE,
                "The settlement file exceeds 1 MiB."
            );
        }

        if (
            rawFileBytes.length >= 3
                && Byte.toUnsignedInt(rawFileBytes[0])
                    == 0xEF
                && Byte.toUnsignedInt(rawFileBytes[1])
                    == 0xBB
                && Byte.toUnsignedInt(rawFileBytes[2])
                    == 0xBF
        ) {
            throw invalid(
                SettlementFileErrorCode
                    .UTF8_BOM_NOT_ALLOWED,
                "A UTF-8 BOM is not allowed."
            );
        }

        for (byte value : rawFileBytes) {
            if (value == 0) {
                throw invalid(
                    SettlementFileErrorCode.NUL_CHARACTER,
                    "NUL characters are not allowed."
                );
            }
        }
    }

    private static String decodeUtf8(
        byte[] rawFileBytes
    ) {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(
                    CodingErrorAction.REPORT
                )
                .onUnmappableCharacter(
                    CodingErrorAction.REPORT
                )
                .decode(
                    ByteBuffer.wrap(rawFileBytes)
                )
                .toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidSettlementFileException(
                SettlementFileErrorCode.MALFORMED_UTF8,
                "The settlement file is not valid UTF-8.",
                exception
            );
        }
    }

    private static List<ParsedSettlementRecord>
        parseRecords(
            String content
        ) {
        try (
            CSVParser parser =
                FORMAT.parse(
                    new StringReader(content)
                )
        ) {
            java.util.Iterator<CSVRecord> iterator =
                parser.iterator();

            if (!iterator.hasNext()) {
                throw invalid(
                    SettlementFileErrorCode.EMPTY_FILE,
                    "The settlement file is empty."
                );
            }

            CSVRecord header = iterator.next();
            validateHeader(header);

            List<ParsedSettlementRecord> records =
                new ArrayList<>();
            Set<String> externalIdentifiers =
                new HashSet<>();

            while (iterator.hasNext()) {
                CSVRecord source = iterator.next();
                int rowNumber =
                    Math.toIntExact(
                        source.getRecordNumber() - 1L
                    );

                if (
                    records.size()
                        == MAX_DATA_ROWS
                ) {
                    throw invalid(
                        SettlementFileErrorCode
                            .TOO_MANY_ROWS,
                        rowNumber,
                        "The settlement file exceeds "
                            + "1,000 data rows."
                    );
                }

                records.add(
                    parseRecord(
                        source,
                        rowNumber,
                        externalIdentifiers
                    )
                );
            }

            if (records.isEmpty()) {
                throw invalid(
                    SettlementFileErrorCode.EMPTY_DATA,
                    "The settlement file contains no "
                        + "data rows."
                );
            }

            return List.copyOf(records);
        } catch (
            InvalidSettlementFileException exception
        ) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidSettlementFileException(
                SettlementFileErrorCode.INVALID_CSV,
                "The settlement file is malformed CSV.",
                exception
            );
        }
    }

    private static void validateHeader(
        CSVRecord header
    ) {
        if (header.size() != HEADER.size()) {
            throw invalid(
                SettlementFileErrorCode.INVALID_HEADER,
                "The settlement header must contain "
                    + "exactly five ordered columns."
            );
        }

        for (int index = 0; index < HEADER.size(); index++) {
            if (
                !HEADER.get(index)
                    .equals(header.get(index))
            ) {
                throw invalid(
                    SettlementFileErrorCode
                        .INVALID_HEADER,
                    "The settlement header does not "
                        + "match the required order."
                );
            }
        }
    }

    private static ParsedSettlementRecord parseRecord(
        CSVRecord source,
        int rowNumber,
        Set<String> externalIdentifiers
    ) {
        if (source.size() != HEADER.size()) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_COLUMN_COUNT,
                rowNumber,
                "Each settlement row must contain "
                    + "exactly five columns."
            );
        }

        List<String> values =
            new ArrayList<>(HEADER.size());

        for (int index = 0; index < HEADER.size(); index++) {
            String value = source.get(index);
            rejectControlCharacters(
                value,
                rowNumber
            );
            values.add(value);
        }

        String externalIdentifier =
            parseExternalIdentifier(
                values.get(0),
                rowNumber,
                externalIdentifiers
            );

        return new ParsedSettlementRecord(
            rowNumber,
            externalIdentifier,
            parsePaymentId(
                values.get(1),
                rowNumber
            ),
            parseAmount(
                values.get(2),
                rowNumber
            ),
            parseCurrency(
                values.get(3),
                rowNumber
            ),
            parseSettledAt(
                values.get(4),
                rowNumber
            )
        );
    }

    private static void rejectControlCharacters(
        String value,
        int rowNumber
    ) {
        for (int index = 0; index < value.length(); index++) {
            if (
                Character.isISOControl(
                    value.charAt(index)
                )
            ) {
                throw invalid(
                    SettlementFileErrorCode
                        .CONTROL_CHARACTER,
                    rowNumber,
                    "Control characters are not allowed "
                        + "in settlement fields."
                );
            }
        }
    }

    private static String parseExternalIdentifier(
        String value,
        int rowNumber,
        Set<String> externalIdentifiers
    ) {
        if (
            !SETTLEMENT_RECORD_ID
                .matcher(value)
                .matches()
        ) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_SETTLEMENT_RECORD_ID,
                rowNumber,
                "The settlement record identifier is "
                    + "invalid."
            );
        }

        if (!externalIdentifiers.add(value)) {
            throw invalid(
                SettlementFileErrorCode
                    .DUPLICATE_SETTLEMENT_RECORD_ID,
                rowNumber,
                "The settlement record identifier is "
                    + "duplicated in this file."
            );
        }

        return value;
    }

    private static UUID parsePaymentId(
        String value,
        int rowNumber
    ) {
        if (value.length() != 36) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_PAYMENT_ID,
                rowNumber,
                "The payment identifier must be a "
                    + "canonical UUID."
            );
        }

        try {
            UUID paymentId = UUID.fromString(value);

            if (!paymentId.toString().equals(value)) {
                throw invalid(
                    SettlementFileErrorCode
                        .INVALID_PAYMENT_ID,
                    rowNumber,
                    "The payment identifier must be a "
                        + "lowercase canonical UUID."
                );
            }

            return paymentId;
        } catch (IllegalArgumentException exception) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_PAYMENT_ID,
                rowNumber,
                "The payment identifier must be a "
                    + "canonical UUID."
            );
        }
    }

    private static long parseAmount(
        String value,
        int rowNumber
    ) {
        if (!AMOUNT.matcher(value).matches()) {
            throw invalid(
                SettlementFileErrorCode.INVALID_AMOUNT,
                rowNumber,
                "The settlement amount must be a "
                    + "positive base-10 integer."
            );
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(
                SettlementFileErrorCode.INVALID_AMOUNT,
                rowNumber,
                "The settlement amount exceeds the "
                    + "supported range."
            );
        }
    }

    private static String parseCurrency(
        String value,
        int rowNumber
    ) {
        if (!"GBP".equals(value)) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_CURRENCY,
                rowNumber,
                "The settlement currency must be GBP."
            );
        }

        return value;
    }

    private static Instant parseSettledAt(
        String value,
        int rowNumber
    ) {
        if (
            value.isEmpty()
                || value.length() > 40
                || !value.endsWith("Z")
        ) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_SETTLED_AT,
                rowNumber,
                "The settlement time must be a bounded "
                    + "UTC ISO-8601 instant."
            );
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(
                SettlementFileErrorCode
                    .INVALID_SETTLED_AT,
                rowNumber,
                "The settlement time must be a valid "
                    + "UTC ISO-8601 instant."
            );
        }
    }

    private static String sha256(
        byte[] rawFileBytes
    ) {
        try {
            byte[] digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(rawFileBytes);

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private static InvalidSettlementFileException
        invalid(
            SettlementFileErrorCode code,
            String message
        ) {
        return new InvalidSettlementFileException(
            code,
            message
        );
    }

    private static InvalidSettlementFileException
        invalid(
            SettlementFileErrorCode code,
            int rowNumber,
            String message
        ) {
        return new InvalidSettlementFileException(
            code,
            rowNumber,
            message
        );
    }
}
