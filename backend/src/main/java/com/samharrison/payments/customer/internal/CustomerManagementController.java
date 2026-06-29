package com.samharrison.payments.customer.internal;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public final class CustomerManagementController {

    private final CustomerManagementService service;

    public CustomerManagementController(
        CustomerManagementService service
    ) {
        this.service = service;
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
            service.create(request.fullName());

        URI location = URI.create(
            "/api/v1/customers/"
                + created.id()
        );

        return ResponseEntity
            .created(location)
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
        @Valid
        @RequestBody
        CustomerRenameRequest request
    ) {
        return ok(
            service.rename(
                customerId,
                request.fullName()
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
        @Valid
        @RequestBody
        CustomerStatusUpdateRequest request
    ) {
        CustomerSnapshot updated =
            switch (request.status()) {
                case ACTIVE ->
                    service.reactivate(customerId);
                case SUSPENDED ->
                    service.suspend(customerId);
                case CLOSED ->
                    service.close(customerId);
            };

        return ok(updated);
    }

    private static ResponseEntity<CustomerResponse>
    ok(
        CustomerSnapshot customer
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(CustomerResponse.from(customer));
    }
}