package com.samharrison.payments.audit;

public class InvalidBusinessAuditEventException
    extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidBusinessAuditEventException(
        String message
    ) {
        super(message);
    }

    public InvalidBusinessAuditEventException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
