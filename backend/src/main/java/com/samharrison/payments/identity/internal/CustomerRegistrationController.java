package com.samharrison.payments.identity.internal;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/api/v1/identity/registrations"
)
public final class CustomerRegistrationController {

    private final CustomerRegistrationService service;

    public CustomerRegistrationController(
        CustomerRegistrationService service
    ) {
        this.service = service;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<
        CustomerRegistrationResponse
        > register(
        @Valid
        @RequestBody
        CustomerRegistrationRequest request
    ) {
        CustomerRegistrationResult result =
            service.register(
                request.email(),
                request.password()
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .cacheControl(CacheControl.noStore())
            .body(
                CustomerRegistrationResponse.from(
                    result
                )
            );
    }
}
