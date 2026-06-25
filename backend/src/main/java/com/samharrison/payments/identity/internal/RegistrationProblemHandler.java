package com.samharrison.payments.identity.internal;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes =
        CustomerRegistrationController.class
)
public final class RegistrationProblemHandler {

    @ExceptionHandler(
        DuplicateEmailException.class
    )
    public ResponseEntity<ProblemDetail>
    handleDuplicateEmail(
        DuplicateEmailException exception
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Registration conflict",
            exception.getMessage(),
            "urn:problem:identity:"
                + "email-already-registered",
            "IDENTITY_EMAIL_ALREADY_REGISTERED"
        );
    }

    @ExceptionHandler(
        InvalidEmailAddressException.class
    )
    public ResponseEntity<ProblemDetail>
    handleInvalidEmail(
        InvalidEmailAddressException exception
    ) {
        return problem(
            HttpStatus.BAD_REQUEST,
            "Invalid email address",
            exception.getMessage(),
            "urn:problem:identity:email-invalid",
            "IDENTITY_EMAIL_INVALID"
        );
    }

    @ExceptionHandler(
        PasswordPolicyException.class
    )
    public ResponseEntity<ProblemDetail>
    handlePasswordPolicy(
        PasswordPolicyException exception
    ) {
        String violation =
            exception.violation()
                .name()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');

        return problem(
            HttpStatus.BAD_REQUEST,
            "Password policy violation",
            exception.getMessage(),
            "urn:problem:identity:password-"
                + violation,
            "IDENTITY_PASSWORD_"
                + exception.violation().name()
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
            "Invalid registration request",
            "The registration request contains "
                + "invalid fields.",
            "urn:problem:identity:"
                + "registration-invalid",
            "IDENTITY_REGISTRATION_INVALID"
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
