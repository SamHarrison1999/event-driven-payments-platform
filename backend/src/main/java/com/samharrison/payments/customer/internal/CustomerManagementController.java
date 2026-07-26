package com.samharrison.payments.customer.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public final class CustomerManagementController {

    private final CustomerManagementService service;

    private final CurrentIdentityUser currentIdentityUser;

    public CustomerManagementController(
        CustomerManagementService service,
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

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerResponse> create(
        @Valid
        @RequestBody
        CustomerCreateRequest request
    ) {
        CustomerSnapshot created =
            service.create(
                request.fullName(),
                currentIdentityUser.requireUserId()
            );

        URI location = URI.create(
            "/api/v1/customers/"
                + created.id()
        );

        return ResponseEntity
            .created(location)
            .header(
                HttpHeaders.ETAG,
                CustomerVersionPrecondition.format(
                    created.version()
                )
            )
            .cacheControl(CacheControl.noStore())
            .body(CustomerResponse.from(created));
    }

    @GetMapping(
        path = "/{customerId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerResponse> find(
        @PathVariable UUID customerId
    ) {
        return ok(
            service.find(customerId)
        );
    }

    @PutMapping(
        path = "/{customerId}/name",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerResponse> rename(
        @PathVariable UUID customerId,
        @RequestHeader(
            value = HttpHeaders.IF_MATCH,
            required = false
        )
        String ifMatch,
        @Valid
        @RequestBody
        CustomerRenameRequest request
    ) {
        long expectedVersion =
            CustomerVersionPrecondition.parseRequired(
                ifMatch
            );

        return ok(
            service.rename(
                customerId,
                request.fullName(),
                expectedVersion
            )
        );
    }

    @PutMapping(
        path = "/{customerId}/status",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerResponse>
    updateStatus(
        @PathVariable UUID customerId,
        @RequestHeader(
            value = HttpHeaders.IF_MATCH,
            required = false
        )
        String ifMatch,
        @Valid
        @RequestBody
        CustomerStatusUpdateRequest request
    ) {
        long expectedVersion =
            CustomerVersionPrecondition.parseRequired(
                ifMatch
            );

        CustomerSnapshot updated =
            switch (request.status()) {
                case ACTIVE ->
                    service.reactivate(
                        customerId,
                        expectedVersion,
                        currentIdentityUser.requireUserId()
                    );
                case SUSPENDED ->
                    service.suspend(
                        customerId,
                        expectedVersion,
                        currentIdentityUser.requireUserId()
                    );
                case CLOSED ->
                    service.close(
                        customerId,
                        expectedVersion,
                        currentIdentityUser.requireUserId()
                    );
            };

        return ok(updated);
    }

    private static ResponseEntity<CustomerResponse>
    ok(
        CustomerSnapshot customer
    ) {
        return ResponseEntity
            .ok()
            .header(
                HttpHeaders.ETAG,
                CustomerVersionPrecondition.format(
                    customer.version()
                )
            )
            .cacheControl(CacheControl.noStore())
            .body(CustomerResponse.from(customer));
    }
}
