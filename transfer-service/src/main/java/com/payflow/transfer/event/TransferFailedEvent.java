package com.payflow.transfer.event;

import java.math.BigDecimal;

public record TransferFailedEvent(
        Long transferId,
        Long senderUserId,
        Long receiverUserId,
        BigDecimal amount,
        String status,
        String failureReason
) {
}
