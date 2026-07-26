package com.samharrison.payments.audit.internal;

import com.samharrison.payments.audit.BusinessAuditEventRequest;
import com.samharrison.payments.audit.BusinessAuditRecorder;
import com.samharrison.payments.audit.InvalidBusinessAuditEventException;
import com.samharrison.payments.audit.RecordedBusinessAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class BusinessAuditRecorderService
    implements BusinessAuditRecorder {

    private final BusinessAuditEventValidator validator;
    private final BusinessAuditMetadataSerializer serializer;
    private final BusinessAuditEventStore store;
    private final Clock clock;

    BusinessAuditRecorderService(
        BusinessAuditEventValidator validator,
        BusinessAuditMetadataSerializer serializer,
        BusinessAuditEventStore store,
        Clock clock
    ) {
        this.validator =
            Objects.requireNonNull(
                validator,
                "validator must not be null"
            );
        this.serializer =
            Objects.requireNonNull(
                serializer,
                "serializer must not be null"
            );
        this.store =
            Objects.requireNonNull(
                store,
                "store must not be null"
            );
        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    @Transactional(
        propagation = Propagation.MANDATORY
    )
    public RecordedBusinessAuditEvent record(
        BusinessAuditEventRequest request
    ) {
        BusinessAuditEventRequest requiredRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        validator.validate(requiredRequest);

        Instant recordedAt =
            clock
                .instant()
                .truncatedTo(ChronoUnit.MICROS);

        if (
            recordedAt.isBefore(
                requiredRequest.occurredAt()
            )
        ) {
            throw new InvalidBusinessAuditEventException(
                "occurredAt cannot be after recordedAt."
            );
        }

        BusinessAuditEvent event =
            BusinessAuditEvent.create(
                requiredRequest,
                recordedAt,
                serializer.write(
                    requiredRequest.metadata()
                )
            );

        return store.record(event);
    }
}
