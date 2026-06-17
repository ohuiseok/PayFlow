package com.payflow.banking.client;

import java.math.BigDecimal;

public record WalletBalanceChangeRequest(
        BigDecimal amount,
        String referenceType,
        String referenceId
) {
}
