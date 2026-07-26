package com.samharrison.payments.audit;

public enum BusinessAuditEventType {
    CUSTOMER_CREATED(
        "customer.created",
        "customer",
        "customer",
        "customer"
    ),
    CUSTOMER_STATUS_CHANGED(
        "customer.status-changed",
        "customer",
        "customer",
        "customer"
    ),
    ACCOUNT_CREATED(
        "account.created",
        "account",
        "account",
        "account"
    ),
    ACCOUNT_STATUS_CHANGED(
        "account.status-changed",
        "account",
        "account",
        "account"
    ),
    IDENTITY_CUSTOMER_ASSIGNED(
        "customer.identity-assigned",
        "customer",
        "customer_identity_assignment",
        "customer"
    ),
    PAYMENT_SUBMITTED(
        "payment.submitted",
        "payment",
        "payment",
        "payment"
    ),
    PAYMENT_COMPLETED(
        "payment.completed",
        "payment",
        "payment",
        "payment"
    ),
    PAYMENT_REJECTED(
        "payment.rejected",
        "payment",
        "payment",
        "payment"
    ),
    PAYMENT_FAILED(
        "payment.failed",
        "payment",
        "payment",
        "payment"
    ),
    SETTLEMENT_IMPORT_ACCEPTED(
        "settlement.import-accepted",
        "reconciliation",
        "settlement_import",
        "settlement_import"
    );

    public static final int SCHEMA_VERSION = 1;

    private final String code;
    private final String sourceModule;
    private final String sourceRecordType;
    private final String subjectType;

    BusinessAuditEventType(
        String code,
        String sourceModule,
        String sourceRecordType,
        String subjectType
    ) {
        this.code = code;
        this.sourceModule = sourceModule;
        this.sourceRecordType = sourceRecordType;
        this.subjectType = subjectType;
    }

    public String code() {
        return code;
    }

    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public String sourceModule() {
        return sourceModule;
    }

    public String sourceRecordType() {
        return sourceRecordType;
    }

    public String subjectType() {
        return subjectType;
    }
}
