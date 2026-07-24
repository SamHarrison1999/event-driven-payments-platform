package com.samharrison.payments.reconciliation.internal;

import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(
    assignableTypes = SettlementImportController.class
)
final class SettlementImportProblemHandler {

    @ExceptionHandler(
        InvalidSettlementFileException.class
    )
    ResponseEntity<ProblemDetail> invalidFile(
        InvalidSettlementFileException failure
    ) {
        ProblemDetail problem =
            createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid settlement file",
                failure.getMessage(),
                "urn:problem:reconciliation:file-invalid",
                failure.code().name()
            );

        if (failure.rowNumber() != null) {
            problem.setProperty(
                "rowNumber",
                failure.rowNumber()
            );
        }

        return response(
            HttpStatus.BAD_REQUEST,
            problem
        );
    }

    @ExceptionHandler(
        InvalidSettlementImportException.class
    )
    ResponseEntity<ProblemDetail> invalidImport(
        InvalidSettlementImportException failure
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid settlement import",
            failure.getMessage(),
            "urn:problem:reconciliation:import-invalid",
            "SETTLEMENT_IMPORT_INVALID"
        );
    }

    @ExceptionHandler(
        SettlementImportConflictException.class
    )
    ResponseEntity<ProblemDetail> conflict(
        SettlementImportConflictException failure
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Settlement import conflict",
            failure.getMessage(),
            "urn:problem:reconciliation:import-conflict",
            "SETTLEMENT_RECORD_ALREADY_IMPORTED"
        );
    }

    @ExceptionHandler(
        SettlementImportNotFoundException.class
    )
    ResponseEntity<ProblemDetail> notFound(
        SettlementImportNotFoundException failure
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Settlement import not found",
            failure.getMessage(),
            "urn:problem:reconciliation:import-not-found",
            "SETTLEMENT_IMPORT_NOT_FOUND"
        );
    }

    @ExceptionHandler({
        MissingServletRequestPartException.class,
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        IOException.class
    })
    ResponseEntity<ProblemDetail> invalidRequest(
        Exception failure
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid settlement import request",
            "The settlement import request could not "
                + "be read or contains invalid values.",
            "urn:problem:reconciliation:request-invalid",
            "SETTLEMENT_IMPORT_REQUEST_INVALID"
        );
    }

    @ExceptionHandler(
        MaxUploadSizeExceededException.class
    )
    ResponseEntity<ProblemDetail> uploadTooLarge() {
        return problem(
            HttpStatus.CONTENT_TOO_LARGE,
            "Settlement file too large",
            "The settlement file exceeds the upload "
                + "size limit.",
            "urn:problem:reconciliation:file-too-large",
            "FILE_TOO_LARGE"
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
