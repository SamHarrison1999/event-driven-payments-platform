package com.samharrison.payments.customer.internal;

import static com.samharrison.payments.customer.CustomerAccountEligibilityException.Reason.NOT_FOUND;

import com.samharrison.payments.customer.CustomerAccountEligibilityException;
import com.samharrison.payments.identity.IdentityUserNotFoundException;
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
        CustomerOwnershipManagementController.class
)
public final class
CustomerOwnershipManagementProblemHandler {

    @ExceptionHandler(
        IdentityUserNotFoundException.class
    )
    public ResponseEntity<ProblemDetail>
    handleIdentityNotFound(
        IdentityUserNotFoundException exception
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Identity user not found",
            exception.getMessage(),
            "urn:problem:identity:user-not-found",
            "IDENTITY_USER_NOT_FOUND"
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
            "Customer cannot receive ownership",
            exception.getMessage(),
            "urn:problem:customer:"
                + "ownership-ineligible",
            "CUSTOMER_OWNERSHIP_INELIGIBLE"
        );
    }

    @ExceptionHandler(
        CustomerOwnershipConflictException.class
    )
    public ResponseEntity<ProblemDetail>
    handleOwnershipConflict(
        CustomerOwnershipConflictException exception
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Customer ownership conflict",
            exception.getMessage(),
            "urn:problem:customer:"
                + "ownership-conflict",
            "CUSTOMER_OWNERSHIP_CONFLICT"
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
        ProblemDetail problem =
            ProblemDetail.forStatusAndDetail(
                status,
                detail
            );

        problem.setTitle(title);
        problem.setType(URI.create(type));
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