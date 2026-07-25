package com.samharrison.payments.reporting.internal;

import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    assignableTypes = AuditEventController.class
)
final class AuditEventProblemHandler {

    @ExceptionHandler({
        InvalidAuditQueryException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> invalidQuery(
        Exception failure
    ) {
        String detail =
            failure
                instanceof InvalidAuditQueryException
                ? failure.getMessage()
                : "An audit search parameter could "
                    + "not be parsed.";

        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                detail
            );

        problem.setTitle("Invalid audit search");
        problem.setType(
            URI.create(
                "urn:problem:reporting:"
                    + "audit-query-invalid"
            )
        );
        problem.setProperty(
            "code",
            "AUDIT_QUERY_INVALID"
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(
                MediaType.APPLICATION_PROBLEM_JSON
            )
            .cacheControl(CacheControl.noStore())
            .body(problem);
    }
}
