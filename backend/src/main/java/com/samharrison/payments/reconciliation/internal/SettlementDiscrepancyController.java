package com.samharrison.payments.reconciliation.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/settlement-discrepancies")
public class SettlementDiscrepancyController {

    private final SettlementDiscrepancyQueryService
        queryService;

    private final SettlementDiscrepancyResolutionService
        resolutionService;

    private final CurrentIdentityUser currentIdentityUser;

    SettlementDiscrepancyController(
        SettlementDiscrepancyQueryService queryService,
        SettlementDiscrepancyResolutionService
            resolutionService,
        CurrentIdentityUser currentIdentityUser
    ) {
        this.queryService =
            Objects.requireNonNull(
                queryService,
                "queryService must not be null"
            );
        this.resolutionService =
            Objects.requireNonNull(
                resolutionService,
                "resolutionService must not be null"
            );
        this.currentIdentityUser =
            Objects.requireNonNull(
                currentIdentityUser,
                "currentIdentityUser must not be null"
            );
    }

    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "List settlement discrepancies",
        description =
            "Returns a bounded keyset page ordered by "
                + "creation time and discrepancy identifier."
    )
    public ResponseEntity<
        SettlementDiscrepancyPageResponse
    > findPage(
        @RequestParam(defaultValue = "OPEN")
        SettlementDiscrepancyStatus status,
        @RequestParam(required = false)
        Instant afterCreatedAt,
        @RequestParam(required = false)
        UUID afterId,
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(100)
        int limit
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                queryService.findPage(
                    status,
                    afterCreatedAt,
                    afterId,
                    limit
                )
            );
    }

    @GetMapping(
        path = "/{discrepancyId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Read a settlement discrepancy")
    public ResponseEntity<SettlementDiscrepancyResponse>
        find(
            @PathVariable UUID discrepancyId
        ) {
        return ok(
            queryService.find(discrepancyId)
        );
    }

    @PutMapping(
        path = "/{discrepancyId}/resolution",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_PROBLEM_JSON_VALUE
        }
    )
    @Operation(
        summary = "Resolve a settlement discrepancy",
        description =
            "Records one immutable attributed decision "
                + "and resolves the discrepancy."
    )
    public ResponseEntity<SettlementDiscrepancyResponse>
        resolve(
            @PathVariable UUID discrepancyId,
            @RequestHeader(
                value = HttpHeaders.IF_MATCH,
                required = false
            )
            String ifMatch,
            @Valid
            @RequestBody
            SettlementResolutionRequest request
        ) {
        long expectedVersion =
            SettlementDiscrepancyVersionPrecondition
                .parseRequired(ifMatch);

        resolutionService.resolve(
            discrepancyId,
            expectedVersion,
            currentIdentityUser.requireUserId(),
            request.decision(),
            request.reason()
        );

        return ok(
            queryService.find(discrepancyId)
        );
    }

    private static ResponseEntity<
        SettlementDiscrepancyResponse
    > ok(
        SettlementDiscrepancyResponse discrepancy
    ) {
        return ResponseEntity
            .ok()
            .header(
                HttpHeaders.ETAG,
                SettlementDiscrepancyVersionPrecondition
                    .format(discrepancy.version())
            )
            .cacheControl(CacheControl.noStore())
            .body(discrepancy);
    }
}
