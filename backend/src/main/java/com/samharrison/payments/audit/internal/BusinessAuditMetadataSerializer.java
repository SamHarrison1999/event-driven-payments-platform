package com.samharrison.payments.audit.internal;

import com.samharrison.payments.audit.InvalidBusinessAuditEventException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class BusinessAuditMetadataSerializer {

    static final int MAX_METADATA_BYTES = 4_096;

    private final JsonMapper objectMapper;

    BusinessAuditMetadataSerializer(
        JsonMapper objectMapper
    ) {
        this.objectMapper =
            Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
            );
    }

    String write(
        Map<String, Object> metadata
    ) {
        try {
            String serialized =
                objectMapper.writeValueAsString(
                    Objects.requireNonNull(
                        metadata,
                        "metadata must not be null"
                    )
                );

            if (
                serialized
                    .getBytes(StandardCharsets.UTF_8)
                    .length
                    > MAX_METADATA_BYTES
            ) {
                throw new
                    InvalidBusinessAuditEventException(
                        "metadata exceeds "
                            + MAX_METADATA_BYTES
                            + " UTF-8 bytes."
                    );
            }

            return serialized;
        } catch (JacksonException failure) {
            throw new InvalidBusinessAuditEventException(
                "metadata could not be serialized.",
                failure
            );
        }
    }
}
