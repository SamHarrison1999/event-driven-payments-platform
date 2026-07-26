package com.samharrison.payments.customer.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public final class CustomerOwnershipManagementController {

    private final CustomerOwnershipManagementService
        service;

    private final CurrentIdentityUser currentIdentityUser;

    public CustomerOwnershipManagementController(
        CustomerOwnershipManagementService service,
        CurrentIdentityUser currentIdentityUser
    ) {
        this.service =
            Objects.requireNonNull(
                service,
                "service must not be null"
            );
        this.currentIdentityUser =
            Objects.requireNonNull(
                currentIdentityUser,
                "currentIdentityUser must not be null"
            );
    }

    @PutMapping(
        path =
            "/{customerId}/identity-users/{identityUserId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerOwnershipResponse>
    assign(
        @PathVariable UUID customerId,
        @PathVariable UUID identityUserId
    ) {
        CustomerOwnershipSnapshot assigned =
            service.assign(
                identityUserId,
                customerId,
                currentIdentityUser.requireUserId()
            );

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                CustomerOwnershipResponse.from(
                    assigned
                )
            );
    }
}
