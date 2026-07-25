package com.samharrison.payments.audit;

public interface BusinessAuditRecorder {

    RecordedBusinessAuditEvent record(
        BusinessAuditEventRequest request
    );
}
