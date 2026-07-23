package com.samharrison.payments.notification.internal;

final class InvalidNotificationStateException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidNotificationStateException(
        String message
    ) {
        super(message);
    }
}
