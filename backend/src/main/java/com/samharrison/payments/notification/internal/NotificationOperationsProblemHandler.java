package com.samharrison.payments.notification.internal;

import com.samharrison.payments.outbox.OutboxDeadLetterNotFoundException;
import com.samharrison.payments.outbox.OutboxReplayConflictException;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {
        NotificationQueryController.class,
        OutboxDeadLetterController.class
    }
)
final class NotificationOperationsProblemHandler {

    @ExceptionHandler(
        OutboxDeadLetterNotFoundException.class
    )
    ResponseEntity<ProblemDetail> notFound(
        OutboxDeadLetterNotFoundException failure
    ) {
        return problem(
            HttpStatus.NOT_FOUND,
            "Outbox event not found",
            failure.getMessage(),
            "urn:problem:outbox:dead-letter-not-found",
            "OUTBOX_DEAD_LETTER_NOT_FOUND"
        );
    }

    @ExceptionHandler(
        OutboxReplayConflictException.class
    )
    ResponseEntity<ProblemDetail> replayConflict(
        OutboxReplayConflictException failure
    ) {
        return problem(
            HttpStatus.CONFLICT,
            "Outbox replay conflict",
            failure.getMessage(),
            "urn:problem:outbox:replay-conflict",
            "OUTBOX_REPLAY_CONFLICT"
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
            .cacheControl(CacheControl.noStore())
            .body(problem);
    }
}
