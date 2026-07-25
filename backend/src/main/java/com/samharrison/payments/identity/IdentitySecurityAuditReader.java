package com.samharrison.payments.identity;

import java.util.List;

public interface IdentitySecurityAuditReader {

    List<IdentitySecurityAuditEvidence> read(
        IdentitySecurityAuditQuery query
    );
}
