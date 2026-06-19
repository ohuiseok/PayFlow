package com.payflow.banking.dto;

import com.payflow.banking.entity.BankingTransfer;
import com.payflow.banking.entity.BankingTransferStatus;
import com.payflow.banking.entity.BankingTransferType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankingTransferResponse(
        Long bankingTransferId,
        BankingTransferType transferType,
        Long bankAccountId,
        Long walletId,
        BigDecimal amount,
        BankingTransferStatus status,
        Long walletTransactionId,
        String failureReason,
        int compensationRetryCount,
        String compensationFailureReason,
        LocalDateTime compensatedAt
) {

    public static BankingTransferResponse from(BankingTransfer transfer) {
        return new BankingTransferResponse(
                transfer.getId(),
                transfer.getTransferType(),
                transfer.getBankAccountId(),
                transfer.getWalletId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getWalletTransactionId(),
                transfer.getFailureReason(),
                transfer.getCompensationRetryCount(),
                transfer.getCompensationFailureReason(),
                transfer.getCompensatedAt()
        );
    }
}
