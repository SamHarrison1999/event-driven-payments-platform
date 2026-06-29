package com.samharrison.payments.account.internal;

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
@RequestMapping("/api/v1/accounts")
public final class AccountManagementController {

    private final AccountManagementService service;

    public AccountManagementController(
        AccountManagementService service
    ) {
        this.service = service;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AccountResponse> create(
        @Valid
        @RequestBody
        AccountCreateRequest request
    ) {
        AccountSnapshot created =
            service.create(request.customerId());

        URI location = URI.create(
            "/api/v1/accounts/"
                + created.id()
        );

        return ResponseEntity
            .created(location)
            .cacheControl(CacheControl.noStore())
            .body(AccountResponse.from(created));
    }

    @GetMapping(
        path = "/{accountId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AccountResponse> find(
        @PathVariable UUID accountId
    ) {
        return ok(
            service.find(accountId)
        );
    }

    @PutMapping(
        path = "/{accountId}/status",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AccountResponse>
    updateStatus(
        @PathVariable UUID accountId,
        @Valid
        @RequestBody
        AccountStatusUpdateRequest request
    ) {
        AccountSnapshot updated =
            switch (request.status()) {
                case ACTIVE ->
                    service.reactivate(accountId);
                case FROZEN ->
                    service.freeze(accountId);
                case CLOSED ->
                    service.close(accountId);
            };

        return ok(updated);
    }

    private static ResponseEntity<AccountResponse>
    ok(
        AccountSnapshot account
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(AccountResponse.from(account));
    }
}