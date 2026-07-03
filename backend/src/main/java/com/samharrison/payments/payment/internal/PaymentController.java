package com.samharrison.payments.payment.internal;

import com.samharrison.payments.identity.CurrentIdentityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public final class PaymentController {

    private final PaymentSubmissionService
        submissionService;

    private final CurrentIdentityUser currentIdentityUser;

    public PaymentController(
        PaymentSubmissionService submissionService,
        CurrentIdentityUser currentIdentityUser
    ) {
        this.submissionService =
            Objects.requireNonNull(
                submissionService,
                "submissionService must not be null"
            );

        this.currentIdentityUser =
            Objects.requireNonNull(
                currentIdentityUser,
                "currentIdentityUser must not be null"
            );
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_PROBLEM_JSON_VALUE
        }
    )
    @Operation(
        summary = "Submit an internal GBP payment",
        description =
            "Creates or safely replays an idempotent "
                + "customer payment using integer GBP "
                + "minor units."
    )
    public ResponseEntity<String> submit(
        @Parameter(
            description =
                "Opaque case-sensitive idempotency key "
                    + "containing 1 to 128 visible ASCII "
                    + "characters.",
            required = true
        )
        @RequestHeader(
            value = PaymentIdempotencyHeader.NAME,
            required = false
        )
        String idempotencyKey,

        @Valid
        @RequestBody
        PaymentCreateRequest request
    ) {
        StoredPaymentResponse response =
            submissionService.submit(
                currentIdentityUser.requireUserId(),
                idempotencyKey,
                request
            );

        return ResponseEntity
            .status(response.status())
            .contentType(
                MediaType.parseMediaType(
                    response.mediaType()
                )
            )
            .cacheControl(CacheControl.noStore())
            .body(response.body());
    }
}
