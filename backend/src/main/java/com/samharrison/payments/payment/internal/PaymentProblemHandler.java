package com.samharrison.payments.payment.internal;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    assignableTypes = PaymentController.class
)
public final class PaymentProblemHandler {

    @ExceptionHandler(
        PaymentNotFoundException.class
    )
    public ResponseEntity<ProblemDetail>
    handlePaymentNotFound(
        PaymentNotFoundException exception
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Payment not found",
            exception.getMessage(),
            "urn:problem:payment:not-found",
            "PAYMENT_NOT_FOUND"
        );
    }

    @ExceptionHandler(
        PaymentIdempotencyKeyRequiredException.class
    )
    public ResponseEntity<ProblemDetail>
    handleMissingIdempotencyKey(
        PaymentIdempotencyKeyRequiredException
            exception
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Idempotency key required",
            exception.getMessage(),
            "urn:problem:payment:"
                + "idempotency-key-required",
            "PAYMENT_IDEMPOTENCY_KEY_REQUIRED"
        );
    }

    @ExceptionHandler(
        InvalidPaymentIdempotencyKeyException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidIdempotencyKey(
        InvalidPaymentIdempotencyKeyException
            exception
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid idempotency key",
            exception.getMessage(),
            "urn:problem:payment:"
                + "idempotency-key-invalid",
            "PAYMENT_IDEMPOTENCY_KEY_INVALID"
        );
    }

    @ExceptionHandler(
        PaymentIdempotencyConflictException.class
    )
    public ResponseEntity<ProblemDetail>
    handleIdempotencyConflict(
        PaymentIdempotencyConflictException
            exception
    ) {
        return switch (exception.reason()) {
            case IDEMPOTENCY_KEY_REUSED ->
                problem(
                    HttpStatus.CONFLICT,
                    "Idempotency key reused",
                    exception.getMessage(),
                    "urn:problem:payment:"
                        + "idempotency-key-reused",
                    "IDEMPOTENCY_KEY_REUSED"
                );
            case IDEMPOTENCY_REQUEST_IN_PROGRESS ->
                problem(
                    HttpStatus.CONFLICT,
                    "Payment request in progress",
                    exception.getMessage(),
                    "urn:problem:payment:"
                        + "idempotency-request-in-progress",
                    "IDEMPOTENCY_REQUEST_IN_PROGRESS"
                );
        };
    }

    @ExceptionHandler(
        InvalidPaymentException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidPayment(
        InvalidPaymentException exception
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid payment request",
            exception.getMessage(),
            "urn:problem:payment:request-invalid",
            "PAYMENT_REQUEST_INVALID"
        );
    }

    @ExceptionHandler(
        MethodArgumentTypeMismatchException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidIdentifier() {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid payment identifier",
            "The payment identifier must be a valid UUID.",
            "urn:problem:payment:identifier-invalid",
            "PAYMENT_IDENTIFIER_INVALID"
        );
    }

    @ExceptionHandler(
        MethodArgumentNotValidException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidRequest(
        MethodArgumentNotValidException exception
    ) {
        List<FieldViolation> violations =
            exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(
                    error ->
                        new FieldViolation(
                            error.getField(),
                            Objects.requireNonNullElse(
                                error.getDefaultMessage(),
                                "Invalid value."
                            )
                        )
                )
                .distinct()
                .sorted(
                    Comparator
                        .comparing(
                            FieldViolation::field
                        )
                        .thenComparing(
                            FieldViolation::message
                        )
                )
                .toList();

        ProblemDetail detail = createProblem(
            HttpStatus.BAD_REQUEST,
            "Invalid payment request",
            "The payment request contains "
                + "invalid fields.",
            "urn:problem:payment:request-invalid",
            "PAYMENT_REQUEST_INVALID"
        );

        detail.setProperty(
            "violations",
            violations
        );

        return response(
            HttpStatus.BAD_REQUEST,
            detail
        );
    }

    @ExceptionHandler(
        HttpMessageNotReadableException.class
    )
    public ResponseEntity<ProblemDetail>
    handleUnreadableRequest() {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Malformed payment request",
            "The payment request body could not be read.",
            "urn:problem:payment:request-malformed",
            "PAYMENT_REQUEST_MALFORMED"
        );
    }

    private static ResponseEntity<ProblemDetail>
    problem(
        HttpStatus status,
        String title,
        String detail,
        String type,
        String code
    ) {
        return response(
            status,
            createProblem(
                status,
                title,
                detail,
                type,
                code
            )
        );
    }

    private static ProblemDetail createProblem(
        HttpStatus status,
        String title,
        String detail,
        String type,
        String code
    ) {
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(
                status,
                detail
            );

        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setProperty("code", code);

        return problem;
    }

    private static ResponseEntity<ProblemDetail>
    response(
        HttpStatus status,
        ProblemDetail problem
    ) {
        return ResponseEntity
            .status(status)
            .contentType(
                MediaType.APPLICATION_PROBLEM_JSON
            )
            .cacheControl(CacheControl.noStore())
            .body(problem);
    }

    private record FieldViolation(
        String field,
        String message
    ) {
    }
}
