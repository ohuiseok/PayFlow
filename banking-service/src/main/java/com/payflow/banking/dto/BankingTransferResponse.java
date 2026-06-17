package com.payflow.banking.dto;

import com.payflow.banking.entity.BankingTransfer;
import com.payflow.banking.entity.BankingTransferStatus;
import java.math.BigDecimal;

public record BankingTransferResponse(
        Long bankingTransferId,
        Long bankAccountId,
        Long walletId,
        BigDecimal amount,
        BankingTransferStatus status,
        Long walletTransactionId,
        String failureReason
) {

    public static BankingTransferResponse from(BankingTransfer transfer) {
        return new BankingTransferResponse(
                transfer.getId(),
                transfer.getBankAccountId(),
                transfer.getWalletId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getWalletTransactionId(),
                transfer.getFailureReason()
        );
    }
}
