package com.samharrison.payments.account;

import java.util.Objects;

public sealed interface AccountPaymentResult
    permits AccountPaymentResult.Approved,
        AccountPaymentResult.Rejected {

    Status status();

    enum Status {
        APPROVED,
        REJECTED
    }

    record Approved(
        AccountPaymentProjection source,
        AccountPaymentProjection destination
    ) implements AccountPaymentResult {

        public Approved {
            source =
                Objects.requireNonNull(
                    source,
                    "source must not be null"
                );

            destination =
                Objects.requireNonNull(
                    destination,
                    "destination must not be null"
                );

            if (
                source.accountId().equals(
                    destination.accountId()
                )
            ) {
                throw new IllegalArgumentException(
                    "Source and destination account "
                        + "projections must be different."
                );
            }
        }

        @Override
        public Status status() {
            return Status.APPROVED;
        }
    }

    record Rejected(
        AccountPaymentRejectionReason reason
    ) implements AccountPaymentResult {

        public Rejected {
            reason =
                Objects.requireNonNull(
                    reason,
                    "reason must not be null"
                );
        }

        @Override
        public Status status() {
            return Status.REJECTED;
        }
    }
}
