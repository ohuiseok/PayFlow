package com.payflow.ledger.event;

import java.math.BigDecimal;

public record TransferCompletedEvent(
        Long transferId,
        Long senderUserId,
        Long receiverUserId,
        BigDecimal amount
) {
}
