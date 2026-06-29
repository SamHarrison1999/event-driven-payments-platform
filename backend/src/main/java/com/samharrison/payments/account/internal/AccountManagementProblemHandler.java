package com.samharrison.payments.account.internal;

import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_FOUND;

import com.samharrison.payments.customer.CustomerAccountEligibilityException;
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

@RestControllerAdvice(
    assignableTypes =
        AccountManagementController.class
)
public final class AccountManagementProblemHandler {

    @ExceptionHandler(
        AccountNotFoundException.class
    )
    public ResponseEntity<ProblemDetail>
    handleAccountNotFound(
        AccountNotFoundException exception
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Account not found",
            exception.getMessage(),
            "urn:problem:account:not-found",
            "ACCOUNT_NOT_FOUND"
        );
    }

    @ExceptionHandler(
        CustomerAccountEligibilityException.class
    )
    public ResponseEntity<ProblemDetail>
    handleCustomerEligibility(
        CustomerAccountEligibilityException exception
    ) {
        if (exception.reason() == NOT_FOUND) {
            return problem(
                HttpStatus.NOT_FOUND,
                "Customer not found",
                exception.getMessage(),
                "urn:problem:customer:not-found",
                "CUSTOMER_NOT_FOUND"
            );
        }

        return problem(
            HttpStatus.CONFLICT,
            "Customer cannot receive account",
            exception.getMessage(),
            "urn:problem:account:"
                + "customer-ineligible",
            "CUSTOMER_ACCOUNT_INELIGIBLE"
        );
    }

    @ExceptionHandler(
        IllegalStateException.class
    )
    public ResponseEntity<ProblemDetail>
    handleLifecycleConflict(
        IllegalStateException exception
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Account lifecycle conflict",
            exception.getMessage(),
            "urn:problem:account:"
                + "lifecycle-conflict",
            "ACCOUNT_LIFECYCLE_CONFLICT"
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
                                error
                                    .getDefaultMessage(),
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
            "Invalid account request",
            "The account request contains "
                + "invalid fields.",
            "urn:problem:account:"
                + "request-invalid",
            "ACCOUNT_REQUEST_INVALID"
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
            "Malformed account request",
            "The account request body could "
                + "not be read.",
            "urn:problem:account:"
                + "request-malformed",
            "ACCOUNT_REQUEST_MALFORMED"
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