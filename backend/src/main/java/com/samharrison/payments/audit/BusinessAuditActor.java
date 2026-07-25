package com.samharrison.payments.audit;

import java.util.Objects;
import java.util.UUID;

public record BusinessAuditActor(
    BusinessAuditActorKind kind,
    UUID identityUserId
) {

    public BusinessAuditActor {
        kind =
            Objects.requireNonNull(
                kind,
                "kind must not be null"
            );

        if (
            kind == BusinessAuditActorKind.IDENTITY_USER
                && identityUserId == null
        ) {
            throw new InvalidBusinessAuditEventException(
                "An identity actor requires an identity "
                    + "user identifier."
            );
        }

        if (
            kind == BusinessAuditActorKind.SYSTEM
                && identityUserId != null
        ) {
            throw new InvalidBusinessAuditEventException(
                "A system actor cannot have an identity "
                    + "user identifier."
            );
        }
    }

    public static BusinessAuditActor identityUser(
        UUID identityUserId
    ) {
        return new BusinessAuditActor(
            BusinessAuditActorKind.IDENTITY_USER,
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            )
        );
    }

    public static BusinessAuditActor system() {
        return new BusinessAuditActor(
            BusinessAuditActorKind.SYSTEM,
            null
        );
    }
}
