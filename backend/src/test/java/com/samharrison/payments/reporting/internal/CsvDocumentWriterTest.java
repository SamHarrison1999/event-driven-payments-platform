package com.samharrison.payments.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvDocumentWriterTest {

    @Test
    void writesQuotedUtf8Rfc4180Records() {
        byte[] content =
            CsvDocumentWriter.write(
                List.of("identifier", "value"),
                List.of(
                    List.of("safe-id", "GBP"),
                    List.of(
                        "quote\"value",
                        "line one\r\nline two"
                    )
                )
            );

        assertThat(
            new String(
                content,
                StandardCharsets.UTF_8
            )
        ).isEqualTo(
            "\"identifier\",\"value\"\r\n"
                + "\"safe-id\",\"GBP\"\r\n"
                + "\"quote\"\"value\","
                + "\"line one\r\nline two\"\r\n"
        );
    }

    @Test
    void rejectsRowsWithUnexpectedWidth() {
        assertThatThrownBy(
            () ->
                CsvDocumentWriter.write(
                    List.of("one", "two"),
                    List.of(List.of("only-one"))
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining("row width");
    }
}
