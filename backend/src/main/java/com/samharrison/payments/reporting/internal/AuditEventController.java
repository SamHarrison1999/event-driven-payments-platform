package com.samharrison.payments.reporting.internal;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public final class AuditEventController {

    private final AuditSearchService searchService;

    public AuditEventController(
        AuditSearchService searchService
    ) {
        this.searchService =
            Objects.requireNonNull(
                searchService,
                "searchService must not be null"
            );
    }

    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Search normalized audit events",
        description =
            "Returns a role-scoped, bounded keyset page "
                + "from canonical and source-owned "
                + "immutable evidence."
    )
    public ResponseEntity<AuditEventPageResponse> search(
        @RequestParam(required = false)
        Instant from,
        @RequestParam(required = false)
        Instant to,
        @RequestParam(required = false)
        AuditCategory category,
        @RequestParam(required = false)
        String eventType,
        @RequestParam(required = false)
        UUID actorIdentityUserId,
        @RequestParam(required = false)
        String subjectType,
        @RequestParam(required = false)
        String subjectIdentifier,
        @RequestParam(required = false)
        String correlationIdentifier,
        @RequestParam(required = false)
        AuditSource source,
        @RequestParam(required = false)
        String cursor,
        @RequestParam(defaultValue = "50")
        int limit
    ) {
        AuditSearchFilter filter =
            new AuditSearchFilter(
                from,
                to,
                category,
                eventType,
                actorIdentityUserId,
                subjectType,
                subjectIdentifier,
                correlationIdentifier,
                source,
                cursor,
                limit
            );

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(searchService.search(filter));
    }
}
