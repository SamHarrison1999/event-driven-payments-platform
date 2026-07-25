package com.samharrison.payments.outbox;

import java.util.List;

public interface OutboxReplayAuditReader {

    List<OutboxReplayAuditEvidence> read(
        OutboxReplayAuditQuery query
    );
}
