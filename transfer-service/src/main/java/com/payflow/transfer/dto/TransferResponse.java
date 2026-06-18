package com.payflow.transfer.dto;

import com.payflow.transfer.entity.Transfer;
import com.payflow.transfer.entity.TransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long transferId,
        Long senderUserId,
        Long receiverUserId,
        BigDecimal amount,
        TransferStatus status,
        String failureReason,
        int compensationRetryCount,
        String compensationFailureReason,
        LocalDateTime compensatedAt,
        LocalDateTime createdAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSenderUserId(),
                transfer.getReceiverUserId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCompensationRetryCount(),
                transfer.getCompensationFailureReason(),
                transfer.getCompensatedAt(),
                transfer.getCreatedAt()
        );
    }
}
