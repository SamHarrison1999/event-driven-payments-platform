package com.samharrison.payments.identity.internal;

import java.util.Objects;
import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(
    String headerName,
    String parameterName,
    String token
) {

    static CsrfTokenResponse from(
        CsrfToken csrfToken
    ) {
        Objects.requireNonNull(
            csrfToken,
            "csrfToken must not be null"
        );

        return new CsrfTokenResponse(
            csrfToken.getHeaderName(),
            csrfToken.getParameterName(),
            csrfToken.getToken()
        );
    }
}
