package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.AccountPaymentRejectionReason.CURRENCY_MISMATCH;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.DESTINATION_NOT_ACTIVE;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.DESTINATION_NOT_FOUND;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.INSUFFICIENT_FUNDS;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.SOURCE_NOT_ACTIVE;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.SOURCE_NOT_FOUND;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.SOURCE_NOT_OWNED;
import static com.samharrison.payments.account.internal.AccountCurrency.GBP;
import static com.samharrison.payments.account.internal.AccountStatus.ACTIVE;

import com.samharrison.payments.account.AccountPaymentMutation;
import com.samharrison.payments.account.AccountPaymentProjection;
import com.samharrison.payments.account.AccountPaymentRejectionReason;
import com.samharrison.payments.account.AccountPaymentResult;
import com.samharrison.payments.customer.CustomerOwnership;
import com.samharrison.payments.shared.GbpAmount;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountPaymentMutationService
    implements AccountPaymentMutation {

    private final CustomerAccountRepository repository;

    private final CustomerOwnership customerOwnership;

    private final Clock clock;

    AccountPaymentMutationService(
        CustomerAccountRepository repository,
        CustomerOwnership customerOwnership,
        Clock clock
    ) {
        this.repository = repository;
        this.customerOwnership = customerOwnership;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AccountPaymentResult apply(
        UUID identityUserId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        GbpAmount amount
    ) {
        UUID requiredIdentityUserId =
            Objects.requireNonNull(
                identityUserId,
                "identityUserId must not be null"
            );

        UUID requiredSourceAccountId =
            Objects.requireNonNull(
                sourceAccountId,
                "sourceAccountId must not be null"
            );

        UUID requiredDestinationAccountId =
            Objects.requireNonNull(
                destinationAccountId,
                "destinationAccountId must not be null"
            );

        GbpAmount requiredAmount =
            Objects.requireNonNull(
                amount,
                "amount must not be null"
            );

        requireValidRequest(
            requiredSourceAccountId,
            requiredDestinationAccountId,
            requiredAmount
        );

        Optional<UUID> customerId =
            customerOwnership.findCustomerId(
                requiredIdentityUserId
            );

        if (customerId.isEmpty()) {
            return rejected(SOURCE_NOT_OWNED);
        }

        Optional<CustomerAccount> sourceLookup =
            repository.findById(
                requiredSourceAccountId
            );

        if (sourceLookup.isEmpty()) {
            return rejected(SOURCE_NOT_FOUND);
        }

        Optional<CustomerAccount> destinationLookup =
            repository.findById(
                requiredDestinationAccountId
            );

        if (destinationLookup.isEmpty()) {
            return rejected(DESTINATION_NOT_FOUND);
        }

        CustomerAccount source = sourceLookup.orElseThrow();
        CustomerAccount destination =
            destinationLookup.orElseThrow();

        AccountPaymentRejectionReason rejection =
            validateAccounts(
                customerId.orElseThrow(),
                source,
                destination,
                requiredAmount
            );

        if (rejection != null) {
            return rejected(rejection);
        }

        Instant changedAt =
            Instant
                .now(clock)
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        requireValidChangeTime(
            changedAt,
            source,
            destination
        );

        validateResultingBalances(
            source,
            destination,
            requiredAmount
        );

        source.debit(
            requiredAmount,
            changedAt
        );

        destination.credit(
            requiredAmount,
            changedAt
        );

        repository.flush();

        return new AccountPaymentResult.Approved(
            toProjection(source),
            toProjection(destination)
        );
    }

    private static void requireValidRequest(
        UUID sourceAccountId,
        UUID destinationAccountId,
        GbpAmount amount
    ) {
        if (
            sourceAccountId.equals(
                destinationAccountId
            )
        ) {
            throw new IllegalArgumentException(
                "Source and destination account "
                    + "identifiers must be different."
            );
        }

        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                "Payment amount must be greater "
                    + "than zero."
            );
        }
    }

    private static AccountPaymentRejectionReason
    validateAccounts(
        UUID customerId,
        CustomerAccount source,
        CustomerAccount destination,
        GbpAmount amount
    ) {
        if (!source.customerId().equals(customerId)) {
            return SOURCE_NOT_OWNED;
        }

        if (source.status() != ACTIVE) {
            return SOURCE_NOT_ACTIVE;
        }

        if (destination.status() != ACTIVE) {
            return DESTINATION_NOT_ACTIVE;
        }

        if (
            source.currency() != GBP
                || destination.currency() != GBP
        ) {
            return CURRENCY_MISMATCH;
        }

        if (amount.compareTo(source.balance()) > 0) {
            return INSUFFICIENT_FUNDS;
        }

        return null;
    }

    private static void requireValidChangeTime(
        Instant changedAt,
        CustomerAccount source,
        CustomerAccount destination
    ) {
        if (
            changedAt.isBefore(source.updatedAt())
                || changedAt.isBefore(
                    destination.updatedAt()
                )
        ) {
            throw new IllegalArgumentException(
                "Payment change time must not be "
                    + "before either account update time."
            );
        }
    }

    private static void validateResultingBalances(
        CustomerAccount source,
        CustomerAccount destination,
        GbpAmount amount
    ) {
        source.balance().minus(amount);
        destination.balance().plus(amount);
    }

    private static AccountPaymentResult.Rejected rejected(
        AccountPaymentRejectionReason reason
    ) {
        return new AccountPaymentResult.Rejected(
            reason
        );
    }

    private static AccountPaymentProjection toProjection(
        CustomerAccount account
    ) {
        return new AccountPaymentProjection(
            account.id(),
            account.customerId(),
            account.balance(),
            account.updatedAt(),
            account.version()
        );
    }
}
