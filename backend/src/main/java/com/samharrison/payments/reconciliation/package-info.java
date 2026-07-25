@ApplicationModule(
    displayName = "Reconciliation",
    allowedDependencies = {
        "audit",
        "identity",
        "payment",
        "shared"
    }
)
package com.samharrison.payments.reconciliation;

import org.springframework.modulith.ApplicationModule;
