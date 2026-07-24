package com.samharrison.payments.reconciliation.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/v1/settlement-imports")
public class SettlementImportController {

    private final SettlementImportService importService;

    private final SettlementImportQueryService
        queryService;

    private final CurrentIdentityUser
        currentIdentityUser;

    SettlementImportController(
        SettlementImportService importService,
        SettlementImportQueryService queryService,
        CurrentIdentityUser currentIdentityUser
    ) {
        this.importService =
            Objects.requireNonNull(
                importService,
                "importService must not be null"
            );
        this.queryService =
            Objects.requireNonNull(
                queryService,
                "queryService must not be null"
            );
        this.currentIdentityUser =
            Objects.requireNonNull(
                currentIdentityUser,
                "currentIdentityUser must not be null"
            );
    }

    @PostMapping(
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_PROBLEM_JSON_VALUE
        }
    )
    @Operation(
        summary = "Import and reconcile a settlement CSV",
        description =
            "Validates one bounded settlement file and "
                + "atomically stores its immutable rows, "
                + "results and discrepancies."
    )
    public ResponseEntity<SettlementImportResponse>
        importFile(
        @RequestPart("file")
        MultipartFile file
    ) throws IOException {
        SettlementImportResponse response =
            importService.importFile(
                currentIdentityUser.requireUserId(),
                file.getOriginalFilename(),
                file.getBytes()
            );

        return ResponseEntity
            .status(
                response.existingImport()
                    ? 200
                    : 201
            )
            .location(
                URI.create(
                    "/api/v1/settlement-imports/"
                        + response.importId()
                )
            )
            .cacheControl(CacheControl.noStore())
            .body(response);
    }

    @GetMapping(
        path = "/{importId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Read a settlement import")
    public ResponseEntity<SettlementImportResponse>
        findImport(
        @PathVariable UUID importId
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(queryService.find(importId));
    }

    @GetMapping(
        path = "/{importId}/results",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "List settlement reconciliation results"
    )
    public ResponseEntity<SettlementResultPageResponse>
        findResults(
            @PathVariable UUID importId,
            @RequestParam(defaultValue = "0")
            @Min(0)
            @Max(1_000)
            int afterRowNumber,
            @RequestParam(defaultValue = "50")
            @Min(1)
            @Max(100)
            int limit
        ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                queryService.findResults(
                    importId,
                    afterRowNumber,
                    limit
                )
            );
    }
}
