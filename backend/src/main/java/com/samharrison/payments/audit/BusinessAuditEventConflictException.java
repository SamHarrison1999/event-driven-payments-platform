package com.samharrison.payments.audit;

public class BusinessAuditEventConflictException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessAuditEventConflictException(
        String sourceEventIdentifier
    ) {
        super(
            "Source event identifier "
                + sourceEventIdentifier
                + " is already associated with different "
                + "immutable audit content."
        );
    }
}
