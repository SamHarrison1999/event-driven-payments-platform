package com.samharrison.payments.notification.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import com.samharrison.payments.outbox.OutboxDeadLetterSnapshot;
import com.samharrison.payments.outbox.OutboxReplayResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping(
    "/api/v1/admin/outbox/dead-letters"
)
public class OutboxDeadLetterController {

    private final OutboxDeadLetterAdminService
        service;

    private final CurrentIdentityUser
        currentIdentityUser;

    public OutboxDeadLetterController(
        OutboxDeadLetterAdminService service,
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

    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "List outbox dead-letter events",
        description =
            "Returns a bounded administrator view of "
                + "outbox events requiring recovery."
    )
    public ResponseEntity<
        List<OutboxDeadLetterSnapshot>
        > findDeadLetters(
            @RequestParam(defaultValue = "50")
            @Min(1)
            @Max(100)
            int limit
        ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                service.findDeadLetters(limit)
            );
    }

    @PostMapping(
        path = "/{eventId}/replay",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_PROBLEM_JSON_VALUE
        }
    )
    @Operation(
        summary = "Replay an outbox dead-letter event",
        description =
            "Returns an eligible immutable event to "
                + "pending publication and records "
                + "administrator replay evidence."
    )
    public ResponseEntity<OutboxReplayResult> replay(
        @PathVariable UUID eventId,
        @Valid
        @RequestBody
        OutboxReplayRequest request
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                service.replay(
                    eventId,
                    currentIdentityUser.requireUserId(),
                    request
                )
            );
    }
}
