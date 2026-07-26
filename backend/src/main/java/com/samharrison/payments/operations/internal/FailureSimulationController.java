package com.samharrison.payments.operations.internal;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/api/v1/operations/failure-simulation"
)
public final class FailureSimulationController {

    private final FailureSimulationService service;

    public FailureSimulationController(
        FailureSimulationService service
    ) {
        this.service = Objects.requireNonNull(
            service,
            "service must not be null"
        );
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Read controlled failure simulation state",
        description =
            "Returns the in-memory failure simulation state."
    )
    public ResponseEntity<FailureSimulationState> current() {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(service.currentState());
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Configure controlled failure simulation",
        description =
            "Configures a bounded, in-memory simulation for "
                + "local resilience testing only."
    )
    public ResponseEntity<FailureSimulationState> configure(
        @Valid @RequestBody FailureSimulationRequest request
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(service.configure(request));
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Clear controlled failure simulation",
        description =
            "Disables the active in-memory simulation."
    )
    public ResponseEntity<FailureSimulationState> clear() {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(service.clear());
    }
}
