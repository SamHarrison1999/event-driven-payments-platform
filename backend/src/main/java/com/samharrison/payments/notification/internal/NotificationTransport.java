package com.samharrison.payments.notification.internal;

interface NotificationTransport {

    void deliver(NotificationDelivery delivery);
}
