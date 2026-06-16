package com.payflow.transfer.client;

import java.math.BigDecimal;

public record WalletBalanceChangeRequest(
        BigDecimal amount,
        String referenceType,
        String referenceId
) {
}
