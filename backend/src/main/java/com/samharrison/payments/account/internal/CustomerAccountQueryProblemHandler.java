package com.samharrison.payments.account.internal;

import com.samharrison.payments.customer.CustomerOwnershipNotFoundException;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes =
        CustomerAccountQueryController.class
)
public final class CustomerAccountQueryProblemHandler {

    @ExceptionHandler(
        CustomerOwnershipNotFoundException.class
    )
    public ResponseEntity<ProblemDetail>
    handleMissingOwnership(
        CustomerOwnershipNotFoundException exception
    ) {
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
            );

        problem.setTitle(
            "Customer ownership not found"
        );

        problem.setType(
            URI.create(
                "urn:problem:customer:"
                    + "ownership-not-found"
            )
        );

        problem.setProperty(
            "code",
            "CUSTOMER_OWNERSHIP_NOT_FOUND"
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .contentType(
                MediaType.APPLICATION_PROBLEM_JSON
            )
            .cacheControl(CacheControl.noStore())
            .body(problem);
    }
}