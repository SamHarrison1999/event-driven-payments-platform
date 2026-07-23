package com.samharrison.payments.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingNotificationTransport
    implements NotificationTransport {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            LoggingNotificationTransport.class
        );

    @Override
    public void deliver(
        NotificationDelivery delivery
    ) {
        LOGGER.info(
            "Simulated payment notification delivered: "
                + "notificationId={}, recipientIdentityUserId={}, "
                + "paymentId={}",
            delivery.notificationId(),
            delivery.recipientIdentityUserId(),
            delivery.paymentId()
        );
    }
}
