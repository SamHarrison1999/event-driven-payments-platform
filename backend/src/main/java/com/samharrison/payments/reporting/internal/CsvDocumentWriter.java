package com.samharrison.payments.reporting.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

final class CsvDocumentWriter {

    private static final String RECORD_SEPARATOR =
        "\r\n";

    private CsvDocumentWriter() {
    }

    static byte[] write(
        List<String> header,
        List<List<String>> rows
    ) {
        List<String> requiredHeader =
            List.copyOf(
                Objects.requireNonNull(
                    header,
                    "header must not be null"
                )
            );
        List<List<String>> requiredRows =
            List.copyOf(
                Objects.requireNonNull(
                    rows,
                    "rows must not be null"
                )
            );

        if (requiredHeader.isEmpty()) {
            throw new IllegalArgumentException(
                "CSV header must not be empty."
            );
        }

        StringBuilder document =
            new StringBuilder();
        appendRecord(document, requiredHeader);

        for (List<String> row : requiredRows) {
            List<String> requiredRow =
                List.copyOf(row);

            if (
                requiredRow.size()
                    != requiredHeader.size()
            ) {
                throw new IllegalArgumentException(
                    "CSV row width must match "
                        + "the header."
                );
            }

            appendRecord(document, requiredRow);
        }

        return document
            .toString()
            .getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRecord(
        StringBuilder document,
        List<String> values
    ) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                document.append(',');
            }

            appendField(document, values.get(index));
        }

        document.append(RECORD_SEPARATOR);
    }

    private static void appendField(
        StringBuilder document,
        String value
    ) {
        document.append('"');

        if (value != null) {
            for (
                int index = 0;
                index < value.length();
                index++
            ) {
                char character =
                    value.charAt(index);

                if (character == '"') {
                    document.append('"');
                }

                document.append(character);
            }
        }

        document.append('"');
    }
}
