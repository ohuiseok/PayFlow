package com.payflow.reward.client;

import java.math.BigDecimal;

public record CreateTransferRequest(
        Long receiverUserId,
        BigDecimal amount
) {
}
