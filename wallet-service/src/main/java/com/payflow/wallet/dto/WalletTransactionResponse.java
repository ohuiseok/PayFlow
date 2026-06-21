package com.payflow.wallet.dto;

import com.payflow.wallet.entity.WalletTransaction;
import com.payflow.wallet.entity.WalletTransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long walletTransactionId,
        Long walletId,
        WalletTransactionType transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String referenceType,
        String referenceId,
        LocalDateTime createdAt
) {

    public static WalletTransactionResponse from(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getWallet().getId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}
