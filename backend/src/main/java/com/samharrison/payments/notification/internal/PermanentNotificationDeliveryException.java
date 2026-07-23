package com.samharrison.payments.notification.internal;

final class PermanentNotificationDeliveryException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    PermanentNotificationDeliveryException(
        String message
    ) {
        super(message);
    }
}
