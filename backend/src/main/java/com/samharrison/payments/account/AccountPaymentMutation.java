package com.samharrison.payments.account;

import com.samharrison.payments.shared.GbpAmount;
import java.util.UUID;

public interface AccountPaymentMutation {

    AccountPaymentResult apply(
        UUID identityUserId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        GbpAmount amount
    );
}
