package com.samharrison.payments.reporting.internal;

import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    assignableTypes = ReportController.class
)
final class ReportProblemHandler {

    @ExceptionHandler({
        InvalidReportQueryException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> invalidQuery(
        Exception failure
    ) {
        String detail =
            failure
                instanceof InvalidReportQueryException
                ? failure.getMessage()
                : "The required report window could "
                    + "not be parsed.";

        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid report query",
            "REPORT_QUERY_INVALID",
            "report-query-invalid",
            detail
        );
    }

    @ExceptionHandler(
        ReportExportTooLargeException.class
    )
    ResponseEntity<ProblemDetail> exportTooLarge(
        ReportExportTooLargeException failure
    ) {
        return problem(
            HttpStatus.valueOf(422),
            "Report export limit exceeded",
            "REPORT_EXPORT_LIMIT_EXCEEDED",
            "report-export-limit-exceeded",
            failure.getMessage()
        );
    }

    private static ResponseEntity<ProblemDetail>
        problem(
            HttpStatus status,
            String title,
            String code,
            String type,
            String detail
        ) {
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(
                status,
                detail
            );
        problem.setTitle(title);
        problem.setType(
            URI.create(
                "urn:problem:reporting:" + type
            )
        );
        problem.setProperty("code", code);

        return ResponseEntity
            .status(status)
            .contentType(
                MediaType.APPLICATION_PROBLEM_JSON
            )
            .cacheControl(CacheControl.noStore())
            .body(problem);
    }
}
