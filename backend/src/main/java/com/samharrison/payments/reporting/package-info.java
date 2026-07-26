@ApplicationModule(
    displayName = "Audit and Operational Reporting",
    allowedDependencies = {
        "account",
        "payment",
        "reconciliation",
        "audit",
        "identity",
        "outbox",
        "shared"
    }
)
package com.samharrison.payments.reporting;

import org.springframework.modulith.ApplicationModule;
