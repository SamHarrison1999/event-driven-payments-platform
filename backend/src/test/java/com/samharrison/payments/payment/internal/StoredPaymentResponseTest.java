package com.samharrison.payments.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoredPaymentResponseTest {

    @Test
    void preservesAllowedJsonResponse() {
        StoredPaymentResponse response =
            new StoredPaymentResponse(
                201,
                StoredPaymentResponse.APPLICATION_JSON,
                """
                {"paymentId":"payment-1001"}
                """.trim()
            );

        assertThat(response.status())
            .isEqualTo(201);

        assertThat(response.mediaType())
            .isEqualTo(
                StoredPaymentResponse.APPLICATION_JSON
            );

        assertThat(response.body())
            .isEqualTo(
                """
                {"paymentId":"payment-1001"}
                """.trim()
            );
    }

    @Test
    void preservesAllowedProblemResponse() {
        StoredPaymentResponse response =
            new StoredPaymentResponse(
                422,
                StoredPaymentResponse
                    .APPLICATION_PROBLEM_JSON,
                """
                {"status":422,"code":"INSUFFICIENT_FUNDS"}
                """.trim()
            );

        assertThat(response.status())
            .isEqualTo(422);

        assertThat(response.mediaType())
            .isEqualTo(
                StoredPaymentResponse
                    .APPLICATION_PROBLEM_JSON
            );
    }

    @Test
    void rejectsInvalidHttpStatus() {
        assertThatThrownBy(
            () ->
                new StoredPaymentResponse(
                    99,
                    StoredPaymentResponse
                        .APPLICATION_JSON,
                    "{}"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "between 100 and 599"
            );

        assertThatThrownBy(
            () ->
                new StoredPaymentResponse(
                    600,
                    StoredPaymentResponse
                        .APPLICATION_JSON,
                    "{}"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "between 100 and 599"
            );
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertThatThrownBy(
            () ->
                new StoredPaymentResponse(
                    201,
                    "text/plain",
                    "{}"
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "not supported"
            );
    }

    @Test
    void rejectsMissingOrBlankBody() {
        assertThatThrownBy(
            () ->
                new StoredPaymentResponse(
                    201,
                    StoredPaymentResponse
                        .APPLICATION_JSON,
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessageContaining(
                "body must not be null"
            );

        assertThatThrownBy(
            () ->
                new StoredPaymentResponse(
                    201,
                    StoredPaymentResponse
                        .APPLICATION_JSON,
                    "   "
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "must not be blank"
            );
    }

    @Test
    void acceptsMaximumUtf8BodySize() {
        String body =
            "\""
                + "a".repeat(
                    StoredPaymentResponse
                        .MAX_BODY_BYTES
                        - 2
                )
                + "\"";

        StoredPaymentResponse response =
            new StoredPaymentResponse(
                201,
                StoredPaymentResponse.APPLICATION_JSON,
                body
            );

        assertThat(
            response
                .body()
                .getBytes(StandardCharsets.UTF_8)
        )
            .hasSize(
                StoredPaymentResponse.MAX_BODY_BYTES
            );
    }

    @Test
    void rejectsBodyAboveUtf8ByteLimit() {
        String body =
            "\""
                + "\u00e9".repeat(
                    StoredPaymentResponse
                        .MAX_BODY_BYTES
                        / 2
                )
                + "\"";

        assertThat(
            body.getBytes(StandardCharsets.UTF_8)
                .length
        )
            .isGreaterThan(
                StoredPaymentResponse.MAX_BODY_BYTES
            );

        assertThatThrownBy(
            () ->
                new StoredPaymentResponse(
                    201,
                    StoredPaymentResponse
                        .APPLICATION_JSON,
                    body
                )
        )
            .isInstanceOf(
                InvalidPaymentException.class
            )
            .hasMessageContaining(
                "UTF-8 bytes"
            );
    }
}