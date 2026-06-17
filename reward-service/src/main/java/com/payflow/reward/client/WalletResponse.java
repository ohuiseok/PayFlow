package com.payflow.reward.client;

import java.math.BigDecimal;

public record WalletResponse(
        Long walletId,
        Long userId,
        BigDecimal balance,
        String status
) {
}
