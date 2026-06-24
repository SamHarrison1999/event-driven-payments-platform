package com.samharrison.payments;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesApplicationModuleBoundaries() {
        ApplicationModules
            .of(PaymentsPlatformBackendApplication.class)
            .verify();
    }
}
