package com.samharrison.payments.customer.internal;

import static com.samharrison.payments.customer.internal.CustomerStatus.ACTIVE;
import static com.samharrison.payments.customer.internal.CustomerStatus.CLOSED;
import static com.samharrison.payments.customer.internal.CustomerStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CustomerProfileTest {

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-06-26T09:00:00Z"
        );

    @Test
    void createsActiveCustomer() {
        CustomerProfile customer =
            createCustomer();

        assertThat(customer.id())
            .isNotNull();

        assertThat(customer.fullName())
            .isEqualTo("Sam Example");

        assertThat(customer.status())
            .isEqualTo(ACTIVE);

        assertThat(customer.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(customer.updatedAt())
            .isEqualTo(CREATED_AT);
    }

    @Test
    void renamesCustomer() {
        CustomerProfile customer =
            createCustomer();

        Instant changedAt =
            CREATED_AT.plusSeconds(60);

        assertThat(
            customer.rename(
                CustomerName.of(
                    "Samuel Example"
                ),
                changedAt
            )
        )
            .isTrue();

        assertThat(customer.fullName())
            .isEqualTo("Samuel Example");

        assertThat(customer.updatedAt())
            .isEqualTo(changedAt);
    }

    @Test
    void identicalRenameIsIdempotent() {
        CustomerProfile customer =
            createCustomer();

        assertThat(
            customer.rename(
                CustomerName.of("Sam Example"),
                CREATED_AT.plusSeconds(60)
            )
        )
            .isFalse();

        assertThat(customer.updatedAt())
            .isEqualTo(CREATED_AT);
    }

    @Test
    void suspendsAndReactivatesCustomer() {
        CustomerProfile customer =
            createCustomer();

        Instant suspendedAt =
            CREATED_AT.plusSeconds(60);

        Instant reactivatedAt =
            suspendedAt.plusSeconds(60);

        assertThat(
            customer.suspend(suspendedAt)
        )
            .isTrue();

        assertThat(customer.status())
            .isEqualTo(SUSPENDED);

        assertThat(
            customer.reactivate(
                reactivatedAt
            )
        )
            .isTrue();

        assertThat(customer.status())
            .isEqualTo(ACTIVE);

        assertThat(customer.updatedAt())
            .isEqualTo(reactivatedAt);
    }

    @Test
    void closesCustomerPermanently() {
        CustomerProfile customer =
            createCustomer();

        Instant closedAt =
            CREATED_AT.plusSeconds(60);

        assertThat(customer.close(closedAt))
            .isTrue();

        assertThat(customer.status())
            .isEqualTo(CLOSED);

        assertThat(
            customer.close(
                closedAt.plusSeconds(60)
            )
        )
            .isFalse();
    }

    @Test
    void closedCustomerCannotBeChanged() {
        CustomerProfile customer =
            createCustomer();

        customer.close(
            CREATED_AT.plusSeconds(60)
        );

        assertThatThrownBy(
            () ->
                customer.rename(
                    CustomerName.of(
                        "Samuel Example"
                    ),
                    CREATED_AT.plusSeconds(120)
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );

        assertThatThrownBy(
            () ->
                customer.reactivate(
                    CREATED_AT.plusSeconds(120)
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            );
    }

    @Test
    void rejectedTimestampDoesNotMutateStatus() {
        CustomerProfile customer =
            createCustomer();

        Instant suspendedAt =
            CREATED_AT.plusSeconds(120);

        customer.suspend(suspendedAt);

        assertThatThrownBy(
            () ->
                customer.reactivate(
                    CREATED_AT.plusSeconds(60)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "previous update time"
            );

        assertThat(customer.status())
            .isEqualTo(SUSPENDED);

        assertThat(customer.updatedAt())
            .isEqualTo(suspendedAt);
    }

    @Test
    void rejectedTimestampDoesNotMutateName() {
        CustomerProfile customer =
            createCustomer();

        Instant suspendedAt =
            CREATED_AT.plusSeconds(120);

        customer.suspend(suspendedAt);

        assertThatThrownBy(
            () ->
                customer.rename(
                    CustomerName.of(
                        "Samuel Example"
                    ),
                    CREATED_AT.plusSeconds(60)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThat(customer.fullName())
            .isEqualTo("Sam Example");

        assertThat(customer.updatedAt())
            .isEqualTo(suspendedAt);
    }

    private static CustomerProfile
    createCustomer() {
        return CustomerProfile.create(
            CustomerName.of("Sam Example"),
            CREATED_AT
        );
    }
}