package com.samharrison.payments.payment.internal;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

record StoredPaymentResponse(
    int status,
    String mediaType,
    String body
) {

    static final int MAX_BODY_BYTES = 16_384;
    static final int MAX_MEDIA_TYPE_LENGTH = 64;

    static final String APPLICATION_JSON =
        "application/json";

    static final String APPLICATION_PROBLEM_JSON =
        "application/problem+json";

    private static final Set<String>
        ALLOWED_MEDIA_TYPES =
            Set.of(
                APPLICATION_JSON,
                APPLICATION_PROBLEM_JSON
            );

    StoredPaymentResponse {
        if (status < 100 || status > 599) {
            throw new InvalidPaymentException(
                "Stored payment response status "
                    + "must be between 100 and 599."
            );
        }

        mediaType =
            Objects.requireNonNull(
                mediaType,
                "mediaType must not be null"
            );

        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new InvalidPaymentException(
                "Stored payment response media "
                    + "type is not supported."
            );
        }

        body =
            Objects.requireNonNull(
                body,
                "body must not be null"
            );

        if (body.isBlank()) {
            throw new InvalidPaymentException(
                "Stored payment response body "
                    + "must not be blank."
            );
        }

        int bodyBytes =
            body
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (bodyBytes > MAX_BODY_BYTES) {
            throw new InvalidPaymentException(
                "Stored payment response body "
                    + "must not exceed "
                    + MAX_BODY_BYTES
                    + " UTF-8 bytes."
            );
        }
    }
}