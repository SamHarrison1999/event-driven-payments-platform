@ApplicationModule(
    displayName = "Customers",
    allowedDependencies = {
        "audit",
        "identity",
        "shared"
    }
)
package com.samharrison.payments.customer;

import org.springframework.modulith.ApplicationModule;