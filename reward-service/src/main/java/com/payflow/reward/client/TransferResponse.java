package com.payflow.reward.client;

import java.math.BigDecimal;

public record TransferResponse(
        Long transferId,
        Long senderUserId,
        Long receiverUserId,
        BigDecimal amount,
        String status,
        String failureReason
) {
}
