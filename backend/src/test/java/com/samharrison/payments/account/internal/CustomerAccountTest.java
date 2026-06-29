package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.internal.AccountCurrency.GBP;
import static com.samharrison.payments.account.internal.AccountStatus.ACTIVE;
import static com.samharrison.payments.account.internal.AccountStatus.CLOSED;
import static com.samharrison.payments.account.internal.AccountStatus.FROZEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samharrison.payments.shared.GbpAmount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerAccountTest {

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-06-29T09:00:00Z"
        );

    @Test
    void createsAnActiveZeroBalanceGbpAccount() {
        UUID customerId =
            UUID.randomUUID();

        CustomerAccount account =
            CustomerAccount.create(
                customerId,
                CREATED_AT
            );

        assertThat(account.id())
            .isNotNull();

        assertThat(account.customerId())
            .isEqualTo(customerId);

        assertThat(account.currency())
            .isEqualTo(GBP);

        assertThat(account.balance())
            .isEqualTo(GbpAmount.ZERO);

        assertThat(account.status())
            .isEqualTo(ACTIVE);

        assertThat(account.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(account.updatedAt())
            .isEqualTo(CREATED_AT);

        assertThat(account.version())
            .isZero();
    }

    @Test
    void creditsAndDebitsAnActiveAccount() {
        CustomerAccount account =
            newAccount();

        Instant creditedAt =
            CREATED_AT.plusSeconds(60);

        assertThat(
            account.credit(
                GbpAmount.ofMinorUnits(5_000L),
                creditedAt
            )
        )
            .isTrue();

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(5_000L)
            );

        assertThat(account.updatedAt())
            .isEqualTo(creditedAt);

        Instant debitedAt =
            creditedAt.plusSeconds(60);

        assertThat(
            account.debit(
                GbpAmount.ofMinorUnits(1_250L),
                debitedAt
            )
        )
            .isTrue();

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(3_750L)
            );

        assertThat(account.updatedAt())
            .isEqualTo(debitedAt);
    }

    @Test
    void permitsCreditsWhileFrozenButRejectsDebits() {
        CustomerAccount account =
            newAccount();

        account.freeze(
            CREATED_AT.plusSeconds(60)
        );

        Instant creditedAt =
            CREATED_AT.plusSeconds(120);

        account.credit(
            GbpAmount.ofMinorUnits(500L),
            creditedAt
        );

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(500L)
            );

        assertThat(account.status())
            .isEqualTo(FROZEN);

        assertThatThrownBy(
            () ->
                account.debit(
                    GbpAmount.ofMinorUnits(100L),
                    creditedAt.plusSeconds(60)
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(500L)
            );
    }

    @Test
    void rejectsInsufficientFundsWithoutMutation() {
        CustomerAccount account =
            newAccount();

        account.credit(
            GbpAmount.ofMinorUnits(500L),
            CREATED_AT.plusSeconds(60)
        );

        Instant previousUpdate =
            account.updatedAt();

        assertThatThrownBy(
            () ->
                account.debit(
                    GbpAmount.ofMinorUnits(501L),
                    CREATED_AT.plusSeconds(120)
                )
        )
            .isInstanceOf(
                InsufficientFundsException.class
            )
            .hasMessageContaining(
                account.id().toString(),
                "GBP 5.00",
                "GBP 5.01"
            );

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(500L)
            );

        assertThat(account.updatedAt())
            .isEqualTo(previousUpdate);
    }

    @Test
    void rejectsZeroValueTransactions() {
        CustomerAccount account =
            newAccount();

        assertThatThrownBy(
            () ->
                account.credit(
                    GbpAmount.ZERO,
                    CREATED_AT.plusSeconds(60)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () ->
                account.debit(
                    GbpAmount.ZERO,
                    CREATED_AT.plusSeconds(60)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThat(account.balance())
            .isEqualTo(GbpAmount.ZERO);

        assertThat(account.updatedAt())
            .isEqualTo(CREATED_AT);
    }

    @Test
    void freezesAndReactivatesIdempotently() {
        CustomerAccount account =
            newAccount();

        Instant frozenAt =
            CREATED_AT.plusSeconds(60);

        assertThat(
            account.freeze(frozenAt)
        )
            .isTrue();

        assertThat(account.status())
            .isEqualTo(FROZEN);

        assertThat(
            account.freeze(
                frozenAt.plusSeconds(60)
            )
        )
            .isFalse();

        assertThat(account.updatedAt())
            .isEqualTo(frozenAt);

        Instant reactivatedAt =
            frozenAt.plusSeconds(120);

        assertThat(
            account.reactivate(reactivatedAt)
        )
            .isTrue();

        assertThat(account.status())
            .isEqualTo(ACTIVE);

        assertThat(
            account.reactivate(
                reactivatedAt.plusSeconds(60)
            )
        )
            .isFalse();

        assertThat(account.updatedAt())
            .isEqualTo(reactivatedAt);
    }

    @Test
    void closesOnlyAZeroBalanceAccountPermanently() {
        CustomerAccount account =
            newAccount();

        Instant closedAt =
            CREATED_AT.plusSeconds(60);

        assertThat(
            account.close(closedAt)
        )
            .isTrue();

        assertThat(account.status())
            .isEqualTo(CLOSED);

        assertThat(
            account.close(
                closedAt.plusSeconds(60)
            )
        )
            .isFalse();

        assertThat(account.updatedAt())
            .isEqualTo(closedAt);

        assertThatThrownBy(
            () ->
                account.credit(
                    GbpAmount.ofMinorUnits(1L),
                    closedAt.plusSeconds(120)
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );

        assertThatThrownBy(
            () ->
                account.reactivate(
                    closedAt.plusSeconds(120)
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );
    }

    @Test
    void rejectsClosingAnAccountWithFunds() {
        CustomerAccount account =
            newAccount();

        account.credit(
            GbpAmount.ofMinorUnits(1L),
            CREATED_AT.plusSeconds(60)
        );

        Instant previousUpdate =
            account.updatedAt();

        assertThatThrownBy(
            () ->
                account.close(
                    CREATED_AT.plusSeconds(120)
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "non-zero balance"
            );

        assertThat(account.status())
            .isEqualTo(ACTIVE);

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(1L)
            );

        assertThat(account.updatedAt())
            .isEqualTo(previousUpdate);
    }

    @Test
    void rejectsEarlierChangeTimesWithoutMutation() {
        CustomerAccount account =
            newAccount();

        Instant creditedAt =
            CREATED_AT.plusSeconds(120);

        account.credit(
            GbpAmount.ofMinorUnits(500L),
            creditedAt
        );

        assertThatThrownBy(
            () ->
                account.credit(
                    GbpAmount.ofMinorUnits(100L),
                    CREATED_AT.plusSeconds(60)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThat(account.balance())
            .isEqualTo(
                GbpAmount.ofMinorUnits(500L)
            );

        assertThat(account.updatedAt())
            .isEqualTo(creditedAt);

        assertThatThrownBy(
            () ->
                account.freeze(CREATED_AT)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThat(account.status())
            .isEqualTo(ACTIVE);

        assertThat(account.updatedAt())
            .isEqualTo(creditedAt);
    }

    private static CustomerAccount newAccount() {
        return CustomerAccount.create(
            UUID.randomUUID(),
            CREATED_AT
        );
    }
}