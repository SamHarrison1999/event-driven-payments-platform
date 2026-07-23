@ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {
        "payment",
        "audit",
        "identity",
        "outbox",
        "shared"
    }
)
package com.samharrison.payments.notification;

import org.springframework.modulith.ApplicationModule;
