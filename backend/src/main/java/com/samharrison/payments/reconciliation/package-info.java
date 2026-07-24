@ApplicationModule(
    displayName = "Reconciliation",
    allowedDependencies = {
        "identity",
        "payment",
        "shared"
    }
)
package com.samharrison.payments.reconciliation;

import org.springframework.modulith.ApplicationModule;
