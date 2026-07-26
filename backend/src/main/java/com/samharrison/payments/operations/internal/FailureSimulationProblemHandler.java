package com.samharrison.payments.operations.internal;

import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = FailureSimulationController.class
)
final class FailureSimulationProblemHandler {

    @ExceptionHandler(FailureSimulationDisabledException.class)
    ResponseEntity<ProblemDetail> disabled(
        FailureSimulationDisabledException failure
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Failure simulation disabled",
            failure.getMessage(),
            "urn:problem:failure-simulation:disabled",
            "FAILURE_SIMULATION_DISABLED"
        );
    }

    @ExceptionHandler(InvalidFailureSimulationException.class)
    ResponseEntity<ProblemDetail> invalid(
        InvalidFailureSimulationException failure
    ) {
        return problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Invalid failure simulation",
            failure.getMessage(),
            "urn:problem:failure-simulation:invalid",
            "INVALID_FAILURE_SIMULATION"
        );
    }

    private static ResponseEntity<ProblemDetail> problem(
        HttpStatus status,
        String title,
        String detail,
        String type,
        String code
    ) {
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setProperty("code", code);

        return ResponseEntity
            .status(status)
            .cacheControl(CacheControl.noStore())
            .body(problem);
    }
}
