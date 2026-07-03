package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.AccountPaymentRejectionReason.DESTINATION_NOT_ACTIVE;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.DESTINATION_NOT_FOUND;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.INSUFFICIENT_FUNDS;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.SOURCE_NOT_ACTIVE;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.SOURCE_NOT_FOUND;
import static com.samharrison.payments.account.AccountPaymentRejectionReason.SOURCE_NOT_OWNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.samharrison.payments.account.AccountPaymentResult;
import com.samharrison.payments.customer.CustomerOwnership;
import com.samharrison.payments.shared.GbpAmount;
import com.samharrison.payments.shared.InvalidGbpAmountException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountPaymentMutationServiceTest {

    private static final UUID IDENTITY_USER_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID CUSTOMER_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    private static final UUID OTHER_CUSTOMER_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-01T09:00:00Z"
        );

    private static final Instant CHANGED_AT =
        Instant.parse(
            "2026-07-03T15:00:00Z"
        );

    @Mock
    private CustomerAccountRepository repository;

    @Mock
    private CustomerOwnership customerOwnership;

    private AccountPaymentMutationService service;

    @BeforeEach
    void setUp() {
        service =
            new AccountPaymentMutationService(
                repository,
                customerOwnership,
                Clock.fixed(
                    CHANGED_AT,
                    ZoneOffset.UTC
                )
            );
    }

    @Test
    void approvedMutationMovesFundsAndReturnsProjections() {
        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                1_000L
            );

        CustomerAccount destination =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                250L
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destination.id(),
                GbpAmount.ofMinorUnits(400L)
            );

        assertThat(result)
            .isInstanceOfSatisfying(
                AccountPaymentResult.Approved.class,
                approved -> {
                    assertThat(
                        approved.source().balance()
                    )
                        .isEqualTo(
                            GbpAmount.ofMinorUnits(
                                600L
                            )
                        );

                    assertThat(
                        approved.destination().balance()
                    )
                        .isEqualTo(
                            GbpAmount.ofMinorUnits(
                                650L
                            )
                        );

                    assertThat(
                        approved.source().updatedAt()
                    )
                        .isEqualTo(CHANGED_AT);

                    assertThat(
                        approved.destination().updatedAt()
                    )
                        .isEqualTo(CHANGED_AT);
                }
            );

        assertThat(source.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(600L)
            );

        assertThat(destination.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(650L)
            );

        verify(repository).flush();
    }

    @Test
    void mutationTimestampIsNormalizedToMicroseconds() {
        Instant nanosecondTime =
            Instant.parse(
                "2026-07-03T15:00:00.123456789Z"
            );

        service =
            new AccountPaymentMutationService(
                repository,
                customerOwnership,
                Clock.fixed(
                    nanosecondTime,
                    ZoneOffset.UTC
                )
            );

        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                1_000L
            );

        CustomerAccount destination =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                250L
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destination.id(),
                GbpAmount.ofMinorUnits(400L)
            );

        Instant expectedTimestamp =
            nanosecondTime.truncatedTo(
                ChronoUnit.MICROS
            );

        assertThat(result)
            .isInstanceOfSatisfying(
                AccountPaymentResult.Approved.class,
                approved -> {
                    assertThat(
                        approved.source().updatedAt()
                    )
                        .isEqualTo(
                            expectedTimestamp
                        );

                    assertThat(
                        approved.destination().updatedAt()
                    )
                        .isEqualTo(
                            expectedTimestamp
                        );
                }
            );

        assertThat(source.updatedAt())
            .isEqualTo(expectedTimestamp);

        assertThat(destination.updatedAt())
            .isEqualTo(expectedTimestamp);

        verify(repository).flush();
    }

    @Test
    void missingOwnershipRejectsBeforeAccountLookup() {
        when(
            customerOwnership.findCustomerId(
                IDENTITY_USER_ID
            )
        )
            .thenReturn(Optional.empty());

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(result, SOURCE_NOT_OWNED);

        verifyNoInteractions(repository);
    }

    @Test
    void missingSourceRejectsWithoutMutation() {
        UUID sourceId = UUID.randomUUID();

        when(
            customerOwnership.findCustomerId(
                IDENTITY_USER_ID
            )
        )
            .thenReturn(
                Optional.of(CUSTOMER_ID)
            );

        when(repository.findById(sourceId))
            .thenReturn(Optional.empty());

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                sourceId,
                UUID.randomUUID(),
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(result, SOURCE_NOT_FOUND);

        verify(repository, never()).flush();
    }

    @Test
    void missingDestinationRejectsWithoutMutation() {
        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                1_000L
            );

        UUID destinationId = UUID.randomUUID();

        when(
            customerOwnership.findCustomerId(
                IDENTITY_USER_ID
            )
        )
            .thenReturn(
                Optional.of(CUSTOMER_ID)
            );

        when(repository.findById(source.id()))
            .thenReturn(Optional.of(source));

        when(repository.findById(destinationId))
            .thenReturn(Optional.empty());

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destinationId,
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(
            result,
            DESTINATION_NOT_FOUND
        );

        assertThat(source.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(1_000L)
            );

        verify(repository, never()).flush();
    }

    @Test
    void sourceOwnedByAnotherCustomerIsRejected() {
        CustomerAccount source =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                1_000L
            );

        CustomerAccount destination =
            fundedAccount(
                CUSTOMER_ID,
                0L
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destination.id(),
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(result, SOURCE_NOT_OWNED);
        assertBalances(source, 1_000L, destination, 0L);
        verify(repository, never()).flush();
    }

    @Test
    void inactiveSourceIsRejectedWithoutMutation() {
        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                1_000L
            );

        source.freeze(CREATED_AT);

        CustomerAccount destination =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                0L
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destination.id(),
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(result, SOURCE_NOT_ACTIVE);
        assertBalances(source, 1_000L, destination, 0L);
        verify(repository, never()).flush();
    }

    @Test
    void inactiveDestinationIsRejectedWithoutMutation() {
        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                1_000L
            );

        CustomerAccount destination =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                0L
            );

        destination.freeze(CREATED_AT);

        stubOwnershipAndAccounts(
            source,
            destination
        );

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destination.id(),
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(
            result,
            DESTINATION_NOT_ACTIVE
        );

        assertBalances(source, 1_000L, destination, 0L);
        verify(repository, never()).flush();
    }

    @Test
    void insufficientFundsAreRejectedWithoutMutation() {
        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                50L
            );

        CustomerAccount destination =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                25L
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        AccountPaymentResult result =
            service.apply(
                IDENTITY_USER_ID,
                source.id(),
                destination.id(),
                GbpAmount.ofMinorUnits(100L)
            );

        assertRejected(result, INSUFFICIENT_FUNDS);
        assertBalances(source, 50L, destination, 25L);
        verify(repository, never()).flush();
    }

    @Test
    void destinationOverflowThrowsBeforeEitherMutation() {
        CustomerAccount source =
            fundedAccount(
                CUSTOMER_ID,
                Long.MAX_VALUE
            );

        CustomerAccount destination =
            fundedAccount(
                OTHER_CUSTOMER_ID,
                1L
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        assertThatThrownBy(
            () ->
                service.apply(
                    IDENTITY_USER_ID,
                    source.id(),
                    destination.id(),
                    GbpAmount.ofMinorUnits(
                        Long.MAX_VALUE
                    )
                )
        )
            .isInstanceOf(
                InvalidGbpAmountException.class
            );

        assertBalances(
            source,
            Long.MAX_VALUE,
            destination,
            1L
        );

        verify(repository, never()).flush();
    }

    @Test
    void earlierClockThrowsBeforeEitherMutation() {
        Instant futureUpdate =
            CHANGED_AT.plusSeconds(60L);

        CustomerAccount source =
            CustomerAccount.create(
                CUSTOMER_ID,
                futureUpdate
            );

        source.credit(
            GbpAmount.ofMinorUnits(1_000L),
            futureUpdate
        );

        CustomerAccount destination =
            CustomerAccount.create(
                OTHER_CUSTOMER_ID,
                futureUpdate
            );

        stubOwnershipAndAccounts(
            source,
            destination
        );

        assertThatThrownBy(
            () ->
                service.apply(
                    IDENTITY_USER_ID,
                    source.id(),
                    destination.id(),
                    GbpAmount.ofMinorUnits(100L)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "before either account"
            );

        assertBalances(source, 1_000L, destination, 0L);
        verify(repository, never()).flush();
    }

    @Test
    void identicalAccountIdentifiersAreInvalid() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(
            () ->
                service.apply(
                    IDENTITY_USER_ID,
                    accountId,
                    accountId,
                    GbpAmount.ofMinorUnits(100L)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "must be different"
            );

        verifyNoInteractions(
            customerOwnership,
            repository
        );
    }

    @Test
    void zeroAmountIsInvalid() {
        assertThatThrownBy(
            () ->
                service.apply(
                    IDENTITY_USER_ID,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    GbpAmount.ZERO
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "greater than zero"
            );

        verifyNoInteractions(
            customerOwnership,
            repository
        );
    }

    private void stubOwnershipAndAccounts(
        CustomerAccount source,
        CustomerAccount destination
    ) {
        when(
            customerOwnership.findCustomerId(
                IDENTITY_USER_ID
            )
        )
            .thenReturn(
                Optional.of(CUSTOMER_ID)
            );

        when(repository.findById(source.id()))
            .thenReturn(Optional.of(source));

        when(repository.findById(destination.id()))
            .thenReturn(
                Optional.of(destination)
            );
    }

    private static CustomerAccount fundedAccount(
        UUID customerId,
        long balanceMinorUnits
    ) {
        CustomerAccount account =
            CustomerAccount.create(
                customerId,
                CREATED_AT
            );

        if (balanceMinorUnits > 0L) {
            account.credit(
                GbpAmount.ofMinorUnits(
                    balanceMinorUnits
                ),
                CREATED_AT
            );
        }

        return account;
    }

    private static void assertRejected(
        AccountPaymentResult result,
        com.samharrison.payments.account.AccountPaymentRejectionReason reason
    ) {
        assertThat(result)
            .isEqualTo(
                new AccountPaymentResult.Rejected(
                    reason
                )
            );
    }

    private static void assertBalances(
        CustomerAccount source,
        long sourceMinorUnits,
        CustomerAccount destination,
        long destinationMinorUnits
    ) {
        assertThat(source.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(
                    sourceMinorUnits
                )
            );

        assertThat(destination.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(
                    destinationMinorUnits
                )
            );
    }
}
