package com.samharrison.payments.reporting.internal;

import java.util.List;

public record AuditEventPageResponse(
    List<AuditEventResponse> events,
    String nextCursor
) {

    public AuditEventPageResponse {
        events = List.copyOf(events);
    }
}
