package com.samharrison.payments.audit;

import java.util.List;

public interface BusinessAuditEvidenceReader {

    List<BusinessAuditEvidence> read(
        BusinessAuditReadCriteria criteria
    );
}
