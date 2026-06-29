package com.samharrison.payments.account.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerAccountRepository
    extends JpaRepository<CustomerAccount, UUID> {

    List<CustomerAccount>
    findAllByCustomerIdOrderByCreatedAtAscIdAsc(
        UUID customerId
    );
}