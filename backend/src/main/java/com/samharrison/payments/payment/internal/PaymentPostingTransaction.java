package com.samharrison.payments.payment.internal;

import com.samharrison.payments.account.AccountPaymentResult;
import com.samharrison.payments.account.AccountPaymentMutation;
import com.samharrison.payments.ledger.LedgerEntrySide;
import com.samharrison.payments.ledger.LedgerPostingCommand;
import com.samharrison.payments.ledger.LedgerPostingEntry;
import com.samharrison.payments.ledger.LedgerPostingService;
import com.samharrison.payments.ledger.PostedLedgerTransaction;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentPostingTransaction {

    private static final String TRANSACTION_TYPE =
        "INTERNAL_PAYMENT";

    private final PaymentRepository paymentRepository;

    private final PaymentIdempotencyRecordRepository
        idempotencyRepository;

    private final AccountPaymentMutation accountPaymentMutation;

    private final LedgerPostingService ledgerPostingService;

    private final Clock clock;

    PaymentPostingTransaction(
        PaymentRepository paymentRepository,
        PaymentIdempotencyRecordRepository
            idempotencyRepository,
        AccountPaymentMutation accountPaymentMutation,
        LedgerPostingService ledgerPostingService,
        Clock clock
    ) {
        this.paymentRepository =
            Objects.requireNonNull(
                paymentRepository,
                "paymentRepository must not be null"
            );

        this.idempotencyRepository =
            Objects.requireNonNull(
                idempotencyRepository,
                "idempotencyRepository must not be null"
            );

        this.accountPaymentMutation =
            Objects.requireNonNull(
                accountPaymentMutation,
                "accountPaymentMutation must not be null"
            );

        this.ledgerPostingService =
            Objects.requireNonNull(
                ledgerPostingService,
                "ledgerPostingService must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public StoredPaymentResponse process(
        UUID paymentId,
        UUID ownerToken
    ) {
        UUID requiredPaymentId =
            Objects.requireNonNull(
                paymentId,
                "paymentId must not be null"
            );

        UUID requiredOwnerToken =
            Objects.requireNonNull(
                ownerToken,
                "ownerToken must not be null"
            );

        Instant processingAt = now();

        PaymentIdempotencyRecord reservation =
            requireActiveReservation(
                requiredPaymentId,
                requiredOwnerToken,
                processingAt
            );

        Payment payment =
            paymentRepository
                .findById(requiredPaymentId)
                .orElseThrow(
                    () ->
                        new InvalidPaymentException(
                            "Reserved payment was not found."
                        )
                );

        payment.startProcessing(processingAt);

        PaymentRequestData request =
            payment.request();

        AccountPaymentResult accountResult =
            accountPaymentMutation.apply(
                payment.actorIdentityId(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount()
            );

        StoredPaymentResponse response;

        if (
            accountResult
                instanceof AccountPaymentResult.Rejected rejected
        ) {
            PaymentRejectionReason reason =
                PaymentRejectionReason.valueOf(
                    rejected.reason().name()
                );

            Instant rejectedAt = now();

            payment.reject(
                reason,
                rejectedAt
            );

            response =
                PaymentResponseFactory.rejected(
                    payment.id(),
                    reason
                );

            reservation.complete(
                requiredOwnerToken,
                response,
                rejectedAt
            );
        } else {
            AccountPaymentResult.Approved approved =
                (AccountPaymentResult.Approved)
                    accountResult;

            PostedLedgerTransaction posted =
                ledgerPostingService.post(
                    ledgerCommand(
                        payment,
                        request,
                        approved
                    )
                );

            Instant completedAt = now();

            payment.complete(
                posted.id(),
                completedAt
            );

            response =
                PaymentResponseFactory.completed(
                    payment.id(),
                    posted.id()
                );

            reservation.complete(
                requiredOwnerToken,
                response,
                completedAt
            );
        }

        paymentRepository.saveAndFlush(payment);

        idempotencyRepository
            .saveAndFlush(reservation);

        return response;
    }

    private PaymentIdempotencyRecord
    requireActiveReservation(
        UUID paymentId,
        UUID ownerToken,
        Instant evaluatedAt
    ) {
        PaymentIdempotencyRecord reservation =
            idempotencyRepository
                .findByPaymentId(paymentId)
                .orElseThrow(
                    () ->
                        new InvalidPaymentException(
                            "Payment reservation was not found."
                        )
                );

        if (!reservation.isOwnedBy(ownerToken)) {
            throw new InvalidPaymentException(
                "Payment reservation owner does not match."
            );
        }

        if (reservation.isLeaseExpired(evaluatedAt)) {
            throw new InvalidPaymentException(
                "Payment reservation lease has expired."
            );
        }

        return reservation;
    }

    private static LedgerPostingCommand ledgerCommand(
        Payment payment,
        PaymentRequestData request,
        AccountPaymentResult.Approved approved
    ) {
        if (
            !approved
                .source()
                .accountId()
                .equals(request.sourceAccountId())
                || !approved
                    .destination()
                    .accountId()
                    .equals(
                        request.destinationAccountId()
                    )
        ) {
            throw new InvalidPaymentException(
                "Account mutation projections do not "
                    + "match the payment request."
            );
        }

        String paymentReference =
            payment.id().toString();

        return new LedgerPostingCommand(
            TRANSACTION_TYPE,
            paymentReference,
            null,
            "Internal payment " + paymentReference,
            List.of(
                new LedgerPostingEntry(
                    request.sourceAccountId(),
                    LedgerEntrySide.DEBIT,
                    request.amount(),
                    "Internal payment source debit"
                ),
                new LedgerPostingEntry(
                    request.destinationAccountId(),
                    LedgerEntrySide.CREDIT,
                    request.amount(),
                    "Internal payment destination credit"
                )
            )
        );
    }

    private Instant now() {
        return Instant
            .now(clock)
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }
}
