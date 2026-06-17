package com.payflow.banking.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateDepositRequest(
        @NotNull Long bankAccountId,
        @NotNull BigDecimal amount
) {
}
