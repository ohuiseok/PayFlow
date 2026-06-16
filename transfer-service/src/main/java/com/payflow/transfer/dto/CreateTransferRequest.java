package com.payflow.transfer.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateTransferRequest(
        @NotNull Long receiverUserId,
        @NotNull BigDecimal amount
) {
}
