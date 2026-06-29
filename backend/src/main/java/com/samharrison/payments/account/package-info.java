@ApplicationModule(
    displayName = "Accounts",
    allowedDependencies = {
        "customer",
        "identity",
        "shared"
    }
)
package com.samharrison.payments.account;

import org.springframework.modulith.ApplicationModule;