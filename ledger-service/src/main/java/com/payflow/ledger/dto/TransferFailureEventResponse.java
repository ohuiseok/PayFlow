package com.payflow.ledger.dto;

import com.payflow.ledger.entity.TransferFailureEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferFailureEventResponse(
        Long transferId,
        Long senderUserId,
        Long receiverUserId,
        BigDecimal amount,
        String status,
        String failureReason,
        LocalDateTime createdAt
) {

    public static TransferFailureEventResponse from(TransferFailureEvent event) {
        return new TransferFailureEventResponse(
                event.getTransferId(),
                event.getSenderUserId(),
                event.getReceiverUserId(),
                event.getAmount(),
                event.getStatus(),
                event.getFailureReason(),
                event.getCreatedAt()
        );
    }
}
