package com.samharrison.payments.audit.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BusinessAuditEventRepository
    extends JpaRepository<BusinessAuditEvent, UUID> {

    @Query(
        """
        SELECT auditEvent
        FROM BusinessAuditEvent auditEvent
        WHERE auditEvent.sourceModule = :sourceModule
          AND auditEvent.eventType = :eventType
          AND auditEvent.sourceRecordType =
              :sourceRecordType
          AND auditEvent.sourceRecordIdentifier =
              :sourceRecordIdentifier
          AND auditEvent.sourceEventIdentifier =
              :sourceEventIdentifier
        """
    )
    Optional<BusinessAuditEvent> findExisting(
        String sourceModule,
        String eventType,
        String sourceRecordType,
        String sourceRecordIdentifier,
        String sourceEventIdentifier
    );
}
