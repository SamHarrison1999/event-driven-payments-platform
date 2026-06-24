package com.samharrison.payments.shared.api;

import com.samharrison.payments.shared.config.PlatformProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final PlatformProperties platformProperties;

    public SystemInfoController(
        PlatformProperties platformProperties
    ) {
        this.platformProperties = platformProperties;
    }

    @GetMapping("/info")
    public ResponseEntity<SystemInfoResponse> getSystemInfo() {
        var response = new SystemInfoResponse(
            platformProperties.name(),
            platformProperties.description(),
            platformProperties.version(),
            platformProperties.educational(),
            platformProperties.realMoneyProcessing()
        );

        return ResponseEntity.ok(response);
    }
}
