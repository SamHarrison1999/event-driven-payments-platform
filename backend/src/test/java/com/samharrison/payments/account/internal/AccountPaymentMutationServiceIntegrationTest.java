package com.samharrison.payments.account.internal;

import static com.samharrison.payments.account.AccountPaymentRejectionReason.INSUFFICIENT_FUNDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.samharrison.payments.account.AccountPaymentMutation;
import com.samharrison.payments.account.AccountPaymentResult;
import com.samharrison.payments.shared.GbpAmount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class AccountPaymentMutationServiceIntegrationTest {

    private static final Instant ACCOUNT_TIME =
        Instant.parse(
            "2020-01-01T00:00:00Z"
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
        new PostgreSQLContainer(
            "postgres:18.4-alpine"
        )
            .withDatabaseName(
                "payments_account_payment_test"
            )
            .withUsername("payments_test")
            .withPassword("payments_test_only");

    @Autowired
    private AccountPaymentMutation service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void approvedMutationPersistsBothBalancesAndVersions() {
        UUID identityUserId = insertIdentityUser();

        UUID sourceCustomerId =
            insertCustomer(
                "Source Customer"
            );

        UUID destinationCustomerId =
            insertCustomer(
                "Destination Customer"
            );

        insertOwnership(
            identityUserId,
            sourceCustomerId
        );

        UUID sourceAccountId =
            insertAccount(
                sourceCustomerId,
                1_000L,
                "ACTIVE",
                0L
            );

        UUID destinationAccountId =
            insertAccount(
                destinationCustomerId,
                250L,
                "ACTIVE",
                0L
            );

        AccountPaymentResult result =
            service.apply(
                identityUserId,
                sourceAccountId,
                destinationAccountId,
                GbpAmount.ofMinorUnits(400L)
            );

        assertThat(result)
            .isInstanceOfSatisfying(
                AccountPaymentResult.Approved.class,
                approved -> {
                    assertThat(
                        approved.source().accountId()
                    )
                        .isEqualTo(sourceAccountId);

                    assertThat(
                        approved.source().customerId()
                    )
                        .isEqualTo(sourceCustomerId);

                    assertThat(
                        approved.source().balance()
                    )
                        .isEqualTo(
                            GbpAmount.ofMinorUnits(
                                600L
                            )
                        );

                    assertThat(
                        approved.source().version()
                    )
                        .isEqualTo(1L);

                    assertThat(
                        approved.destination().accountId()
                    )
                        .isEqualTo(destinationAccountId);

                    assertThat(
                        approved.destination().customerId()
                    )
                        .isEqualTo(
                            destinationCustomerId
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
                        approved.destination().version()
                    )
                        .isEqualTo(1L);

                    assertThat(
                        approved.source().updatedAt()
                    )
                        .isEqualTo(
                            approved
                                .destination()
                                .updatedAt()
                        );

                    assertThat(
                        approved.source().updatedAt()
                    )
                        .isAfter(ACCOUNT_TIME);
                }
            );

        AccountState sourceState =
            findAccountState(sourceAccountId);

        AccountState destinationState =
            findAccountState(
                destinationAccountId
            );

        assertThat(sourceState.balanceMinorUnits())
            .isEqualTo(600L);

        assertThat(sourceState.version())
            .isEqualTo(1L);

        assertThat(destinationState.balanceMinorUnits())
            .isEqualTo(650L);

        assertThat(destinationState.version())
            .isEqualTo(1L);

        assertThat(sourceState.updatedAt())
            .isEqualTo(
                destinationState.updatedAt()
            );

        AccountPaymentResult.Approved approved =
            (AccountPaymentResult.Approved) result;

        assertThat(sourceState.updatedAt())
            .isEqualTo(
                approved.source().updatedAt()
            );
    }

    @Test
    void rejectedMutationLeavesBothRowsUnchanged() {
        UUID identityUserId = insertIdentityUser();

        UUID sourceCustomerId =
            insertCustomer(
                "Rejected Source Customer"
            );

        UUID destinationCustomerId =
            insertCustomer(
                "Rejected Destination Customer"
            );

        insertOwnership(
            identityUserId,
            sourceCustomerId
        );

        UUID sourceAccountId =
            insertAccount(
                sourceCustomerId,
                50L,
                "ACTIVE",
                2L
            );

        UUID destinationAccountId =
            insertAccount(
                destinationCustomerId,
                25L,
                "ACTIVE",
                4L
            );

        AccountState sourceBefore =
            findAccountState(sourceAccountId);

        AccountState destinationBefore =
            findAccountState(
                destinationAccountId
            );

        AccountPaymentResult result =
            service.apply(
                identityUserId,
                sourceAccountId,
                destinationAccountId,
                GbpAmount.ofMinorUnits(100L)
            );

        assertThat(result)
            .isEqualTo(
                new AccountPaymentResult.Rejected(
                    INSUFFICIENT_FUNDS
                )
            );

        assertThat(
            findAccountState(sourceAccountId)
        )
            .isEqualTo(sourceBefore);

        assertThat(
            findAccountState(
                destinationAccountId
            )
        )
            .isEqualTo(destinationBefore);
    }

    private UUID insertIdentityUser() {
        UUID identityUserId =
            UUID.randomUUID();

        String email =
            identityUserId
                + "@example.com";

        jdbcTemplate.update(
            """
            INSERT INTO identity_user (
                id,
                email,
                normalized_email,
                password_hash,
                status,
                failed_login_attempts,
                locked_until,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            identityUserId,
            email,
            email,
            "{noop}not-used-in-this-test",
            "ACTIVE",
            0,
            null,
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        jdbcTemplate.update(
            """
            INSERT INTO identity_user_role (
                user_id,
                role_code
            )
            VALUES (?, ?)
            """,
            identityUserId,
            "CUSTOMER"
        );

        return identityUserId;
    }

    private UUID insertCustomer(
        String fullName
    ) {
        UUID customerId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO customer_profile (
                id,
                full_name,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            customerId,
            fullName,
            "ACTIVE",
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );

        return customerId;
    }

    private void insertOwnership(
        UUID identityUserId,
        UUID customerId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO customer_identity_assignment (
                identity_user_id,
                customer_id,
                assigned_at,
                version
            )
            VALUES (?, ?, ?, ?)
            """,
            identityUserId,
            customerId,
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            0L
        );
    }

    private UUID insertAccount(
        UUID customerId,
        long balanceMinorUnits,
        String status,
        long version
    ) {
        UUID accountId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO customer_account (
                id,
                customer_id,
                currency,
                balance_minor_units,
                status,
                created_at,
                updated_at,
                version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            accountId,
            customerId,
            "GBP",
            balanceMinorUnits,
            status,
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            ACCOUNT_TIME.atOffset(
                ZoneOffset.UTC
            ),
            version
        );

        return accountId;
    }

    private AccountState findAccountState(
        UUID accountId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                balance_minor_units,
                updated_at,
                version
            FROM customer_account
            WHERE id = ?
            """,
            (
                resultSet,
                rowNumber
            ) ->
                toAccountState(resultSet),
            accountId
        );
    }

    private static AccountState toAccountState(
        ResultSet resultSet
    ) throws SQLException {
        return new AccountState(
            resultSet.getLong(
                "balance_minor_units"
            ),
            resultSet
                .getObject(
                    "updated_at",
                    OffsetDateTime.class
                )
                .toInstant(),
            resultSet.getLong("version")
        );
    }

    private record AccountState(
        long balanceMinorUnits,
        Instant updatedAt,
        long version
    ) {
    }
}