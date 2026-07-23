package com.samharrison.payments.notification.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationQueryController {

    private final NotificationQueryService service;

    private final CurrentIdentityUser
        currentIdentityUser;

    public NotificationQueryController(
        NotificationQueryService service,
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
        summary = "List owned notifications",
        description =
            "Returns simulated payment notifications "
                + "addressed to the authenticated customer."
    )
    public ResponseEntity<List<NotificationResponse>>
        findOwned(
            @RequestParam(defaultValue = "50")
            @Min(1)
            @Max(100)
            int limit
        ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(
                service.findOwned(
                    currentIdentityUser.requireUserId(),
                    limit
                )
            );
    }
}
