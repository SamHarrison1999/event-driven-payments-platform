@ApplicationModule(
    displayName = "Payments",
    allowedDependencies = {
        "account",
        "ledger",
        "risk",
        "audit",
        "identity",
        "outbox",
        "shared"
    }
)
package com.samharrison.payments.payment;

import org.springframework.modulith.ApplicationModule;
