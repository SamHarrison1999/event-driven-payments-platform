package com.samharrison.payments.reporting.internal;

import java.util.Arrays;
import java.util.Objects;

record CsvReport(
    String filename,
    byte[] content
) {

    CsvReport {
        filename =
            Objects.requireNonNull(
                filename,
                "filename must not be null"
            );
        content =
            Arrays.copyOf(
                Objects.requireNonNull(
                    content,
                    "content must not be null"
                ),
                content.length
            );
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
