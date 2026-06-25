package com.samharrison.payments.identity.internal;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/api/v1/identity/users/{userId}/roles"
)
public final class IdentityRoleManagementController {

    private final IdentityRoleManagementService service;

    public IdentityRoleManagementController(
        IdentityRoleManagementService service
    ) {
        this.service = service;
    }

    @PutMapping(
        path = "/{role}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<IdentityRolesResponse>
    grantRole(
        @PathVariable UUID userId,
        @PathVariable IdentityRole role
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                service.grantRole(
                    userId,
                    role
                )
            );
    }

    @DeleteMapping(
        path = "/{role}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<IdentityRolesResponse>
    revokeRole(
        @PathVariable UUID userId,
        @PathVariable IdentityRole role
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                service.revokeRole(
                    userId,
                    role
                )
            );
    }

    @ExceptionHandler(
        IdentityUserNotFoundException.class
    )
    ResponseEntity<Void> identityUserNotFound() {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .cacheControl(CacheControl.noStore())
            .build();
    }

    @ExceptionHandler(
        LastIdentityRoleRemovalException.class
    )
    ResponseEntity<Void> finalRoleRemovalRejected() {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .cacheControl(CacheControl.noStore())
            .build();
    }
}
