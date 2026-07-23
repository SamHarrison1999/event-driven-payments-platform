package com.samharrison.payments.notification.internal;

final class InvalidNotificationEventException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidNotificationEventException(
        String message
    ) {
        super(message);
    }

    InvalidNotificationEventException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
