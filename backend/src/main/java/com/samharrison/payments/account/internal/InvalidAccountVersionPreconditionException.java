package com.samharrison.payments.account.internal;

import java.io.Serial;

public final class
InvalidAccountVersionPreconditionException
    extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidAccountVersionPreconditionException(
        String rawHeader
    ) {
        super(
            "The If-Match header must contain one "
                + "strong account-version ETag, "
                + "for example \"0\". Received: "
                + String.valueOf(rawHeader)
        );
    }
}