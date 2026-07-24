package com.samharrison.payments.reconciliation.internal;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
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
        SettlementDiscrepancyController.class
)
final class SettlementDiscrepancyProblemHandler {

    @ExceptionHandler(
        SettlementDiscrepancyNotFoundException.class
    )
    ResponseEntity<ProblemDetail> notFound(
        SettlementDiscrepancyNotFoundException failure
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Settlement discrepancy not found",
            failure.getMessage(),
            "urn:problem:reconciliation:"
                + "discrepancy-not-found",
            "SETTLEMENT_DISCREPANCY_NOT_FOUND"
        );
    }

    @ExceptionHandler(
        SettlementDiscrepancyVersionRequiredException
            .class
    )
    ResponseEntity<ProblemDetail> versionRequired(
        SettlementDiscrepancyVersionRequiredException
            failure
    ) {
        return problem(
            HttpStatus.PRECONDITION_REQUIRED,
            "Settlement discrepancy version required",
            failure.getMessage(),
            "urn:problem:reconciliation:"
                + "discrepancy-version-required",
            "SETTLEMENT_DISCREPANCY_VERSION_REQUIRED"
        );
    }

    @ExceptionHandler(
        InvalidSettlementDiscrepancyVersionException
            .class
    )
    ResponseEntity<ProblemDetail> invalidVersion(
        InvalidSettlementDiscrepancyVersionException
            failure
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid settlement discrepancy version",
            failure.getMessage(),
            "urn:problem:reconciliation:"
                + "discrepancy-version-invalid",
            "SETTLEMENT_DISCREPANCY_VERSION_INVALID"
        );
    }

    @ExceptionHandler(
        SettlementDiscrepancyVersionConflictException
            .class
    )
    ResponseEntity<ProblemDetail> versionConflict(
        SettlementDiscrepancyVersionConflictException
            failure
    ) {
        ProblemDetail problem =
            createProblem(
                HttpStatus.PRECONDITION_FAILED,
                "Settlement discrepancy version conflict",
                failure.getMessage(),
                "urn:problem:reconciliation:"
                    + "discrepancy-version-conflict",
                "SETTLEMENT_DISCREPANCY_VERSION_CONFLICT"
            );

        problem.setProperty(
            "discrepancyId",
            failure.discrepancyId()
        );
        problem.setProperty(
            "expectedVersion",
            failure.expectedVersion()
        );
        problem.setProperty(
            "actualVersion",
            failure.actualVersion()
        );

        return response(
            HttpStatus.PRECONDITION_FAILED,
            problem
        );
    }

    @ExceptionHandler(
        SettlementDiscrepancyLifecycleException.class
    )
    ResponseEntity<ProblemDetail> lifecycleConflict(
        SettlementDiscrepancyLifecycleException failure
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Settlement discrepancy already resolved",
            failure.getMessage(),
            "urn:problem:reconciliation:"
                + "discrepancy-resolved",
            "SETTLEMENT_DISCREPANCY_ALREADY_RESOLVED"
        );
    }

    @ExceptionHandler({
        InvalidSettlementResolutionException.class,
        InvalidSettlementDiscrepancyQueryException.class
    })
    ResponseEntity<ProblemDetail> invalidOperation(
        RuntimeException failure
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid settlement discrepancy request",
            failure.getMessage(),
            "urn:problem:reconciliation:"
                + "discrepancy-request-invalid",
            "SETTLEMENT_DISCREPANCY_REQUEST_INVALID"
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> invalidRequest() {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid settlement discrepancy request",
            "The settlement discrepancy request could "
                + "not be read or contains invalid values.",
            "urn:problem:reconciliation:"
                + "discrepancy-request-invalid",
            "SETTLEMENT_DISCREPANCY_REQUEST_INVALID"
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
}
