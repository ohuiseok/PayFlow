package com.payflow.wallet.dto;

import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.entity.WalletStatus;
import java.math.BigDecimal;

public record WalletResponse(
        Long walletId,
        Long userId,
        BigDecimal balance,
        WalletStatus status
) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getStatus()
        );
    }
}
