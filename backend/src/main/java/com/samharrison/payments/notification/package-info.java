@ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {
        "payment",
        "audit",
        "shared"
    }
)
package com.samharrison.payments.notification;

import org.springframework.modulith.ApplicationModule;
