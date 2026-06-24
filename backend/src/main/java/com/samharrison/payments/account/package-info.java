@ApplicationModule(
    displayName = "Accounts",
    allowedDependencies = {
        "customer",
        "shared"
    }
)
package com.samharrison.payments.account;

import org.springframework.modulith.ApplicationModule;
