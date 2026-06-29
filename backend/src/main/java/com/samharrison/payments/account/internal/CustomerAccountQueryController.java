package com.samharrison.payments.account.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CustomerAccountQueryController {

    private final CustomerAccountQueryService service;

    private final CurrentIdentityUser currentIdentityUser;

    public CustomerAccountQueryController(
        CustomerAccountQueryService service,
        CurrentIdentityUser currentIdentityUser
    ) {
        this.service = service;
        this.currentIdentityUser =
            currentIdentityUser;
    }

    @GetMapping(
        path = "/api/v1/accounts",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<AccountResponse>>
    findOwnedAccounts() {
        return ok(
            service.findOwnedByIdentityUser(
                currentIdentityUser.requireUserId()
            )
        );
    }

    @GetMapping(
        path =
            "/api/v1/customers/{customerId}/accounts",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<AccountResponse>>
    findCustomerAccounts(
        @PathVariable UUID customerId
    ) {
        return ok(
            service.findByCustomerId(customerId)
        );
    }

    private static ResponseEntity<List<AccountResponse>>
    ok(
        List<AccountSnapshot> accounts
    ) {
        List<AccountResponse> response =
            accounts
                .stream()
                .map(AccountResponse::from)
                .toList();

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(response);
    }
}