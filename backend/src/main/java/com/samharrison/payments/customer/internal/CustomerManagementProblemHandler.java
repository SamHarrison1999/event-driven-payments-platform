package com.samharrison.payments.customer.internal;

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
    assignableTypes =
        CustomerManagementController.class
)
public final class CustomerManagementProblemHandler {

    @ExceptionHandler(
        CustomerNotFoundException.class
    )
    public ResponseEntity<ProblemDetail>
    handleNotFound(
        CustomerNotFoundException exception
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Customer not found",
            exception.getMessage(),
            "urn:problem:customer:not-found",
            "CUSTOMER_NOT_FOUND"
        );
    }

    @ExceptionHandler(
        CustomerVersionPreconditionRequiredException.class
    )
    public ResponseEntity<ProblemDetail>
    handleVersionRequired(
        CustomerVersionPreconditionRequiredException
            exception
    ) {
        return problem(
            HttpStatus.PRECONDITION_REQUIRED,
            "Customer version required",
            exception.getMessage(),
            "urn:problem:customer:"
                + "version-required",
            "CUSTOMER_VERSION_REQUIRED"
        );
    }

    @ExceptionHandler(
        InvalidCustomerVersionPreconditionException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidVersion(
        InvalidCustomerVersionPreconditionException
            exception
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid customer version",
            exception.getMessage(),
            "urn:problem:customer:"
                + "version-invalid",
            "CUSTOMER_VERSION_INVALID"
        );
    }

    @ExceptionHandler(
        CustomerVersionConflictException.class
    )
    public ResponseEntity<ProblemDetail>
    handleVersionConflict(
        CustomerVersionConflictException exception
    ) {
        ProblemDetail detail = createProblem(
            HttpStatus.PRECONDITION_FAILED,
            "Customer version conflict",
            exception.getMessage(),
            "urn:problem:customer:"
                + "version-conflict",
            "CUSTOMER_VERSION_CONFLICT"
        );

        detail.setProperty(
            "customerId",
            exception.customerId()
        );

        detail.setProperty(
            "expectedVersion",
            exception.expectedVersion()
        );

        detail.setProperty(
            "actualVersion",
            exception.actualVersion()
        );

        return response(
            HttpStatus.PRECONDITION_FAILED,
            detail
        );
    }
    @ExceptionHandler(
        InvalidCustomerNameException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidName(
        InvalidCustomerNameException exception
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid customer name",
            exception.getMessage(),
            "urn:problem:customer:name-invalid",
            "CUSTOMER_NAME_INVALID"
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
            "Customer lifecycle conflict",
            exception.getMessage(),
            "urn:problem:customer:"
                + "lifecycle-conflict",
            "CUSTOMER_LIFECYCLE_CONFLICT"
        );
    }

    @ExceptionHandler(
        MethodArgumentTypeMismatchException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidIdentifier() {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid customer identifier",
            "The customer identifier must be "
                + "a valid UUID.",
            "urn:problem:customer:"
                + "identifier-invalid",
            "CUSTOMER_IDENTIFIER_INVALID"
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
            "Invalid customer request",
            "The customer request contains "
                + "invalid fields.",
            "urn:problem:customer:"
                + "request-invalid",
            "CUSTOMER_REQUEST_INVALID"
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
            "Malformed customer request",
            "The customer request body could "
                + "not be read.",
            "urn:problem:customer:"
                + "request-malformed",
            "CUSTOMER_REQUEST_MALFORMED"
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