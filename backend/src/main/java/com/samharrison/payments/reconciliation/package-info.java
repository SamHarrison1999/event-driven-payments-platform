@ApplicationModule(
    displayName = "Reconciliation",
    allowedDependencies = {
        "payment",
        "audit",
        "shared"
    }
)
package com.samharrison.payments.reconciliation;

import org.springframework.modulith.ApplicationModule;
