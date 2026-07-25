CREATE INDEX idx_payment_created_report
    ON payment (
        created_at,
        id
    );

CREATE INDEX idx_settlement_import_completed_report
    ON settlement_import (
        completed_at,
        id
    )
    WHERE status = 'COMPLETED';

CREATE INDEX idx_settlement_discrepancy_created_report
    ON settlement_discrepancy (
        created_at,
        id
    );

CREATE INDEX idx_settlement_resolution_decision_report
    ON settlement_resolution (
        decision,
        decided_at,
        id
    );
