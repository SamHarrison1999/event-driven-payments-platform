package com.samharrison.payments.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SettlementDiscrepancyVersionPreconditionTest {

    @Test
    void parsesAndFormatsOneStrongVersionEtag() {
        assertThat(
            SettlementDiscrepancyVersionPrecondition
                .parseRequired("\"42\"")
        )
            .isEqualTo(42L);

        assertThat(
            SettlementDiscrepancyVersionPrecondition
                .format(42L)
        )
            .isEqualTo("\"42\"");
    }

    @Test
    void rejectsMissingAndWeakEtags() {
        assertThatThrownBy(
            () ->
                SettlementDiscrepancyVersionPrecondition
                    .parseRequired(null)
        )
            .isInstanceOf(
                SettlementDiscrepancyVersionRequiredException
                    .class
            );

        assertThatThrownBy(
            () ->
                SettlementDiscrepancyVersionPrecondition
                    .parseRequired("W/\"0\"")
        )
            .isInstanceOf(
                InvalidSettlementDiscrepancyVersionException
                    .class
            );
    }

    @Test
    void rejectsListsWildcardsAndInvalidNumbers() {
        assertThatThrownBy(
            () ->
                SettlementDiscrepancyVersionPrecondition
                    .parseRequired("\"0\", \"1\"")
        )
            .isInstanceOf(
                InvalidSettlementDiscrepancyVersionException
                    .class
            );

        assertThatThrownBy(
            () ->
                SettlementDiscrepancyVersionPrecondition
                    .parseRequired("*")
        )
            .isInstanceOf(
                InvalidSettlementDiscrepancyVersionException
                    .class
            );

        assertThatThrownBy(
            () ->
                SettlementDiscrepancyVersionPrecondition
                    .parseRequired("\"01\"")
        )
            .isInstanceOf(
                InvalidSettlementDiscrepancyVersionException
                    .class
            );
    }
}
