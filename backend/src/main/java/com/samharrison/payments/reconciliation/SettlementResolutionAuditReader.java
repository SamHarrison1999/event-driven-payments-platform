package com.samharrison.payments.reconciliation;

import java.util.List;

public interface SettlementResolutionAuditReader {

    List<SettlementResolutionAuditEvidence> read(
        SettlementResolutionAuditQuery query
    );
}
