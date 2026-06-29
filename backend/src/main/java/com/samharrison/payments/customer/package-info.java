@ApplicationModule(
    displayName = "Customers",
    allowedDependencies = {
        "identity",
        "shared"
    }
)
package com.samharrison.payments.customer;

import org.springframework.modulith.ApplicationModule;