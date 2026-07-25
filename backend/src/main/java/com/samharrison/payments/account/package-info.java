@ApplicationModule(
    displayName = "Accounts",
    allowedDependencies = {
        "audit",
        "customer",
        "identity",
        "shared"
    }
)
package com.samharrison.payments.account;

import org.springframework.modulith.ApplicationModule;