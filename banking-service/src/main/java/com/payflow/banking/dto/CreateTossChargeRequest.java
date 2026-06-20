package com.payflow.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTossChargeRequest(
        @NotNull
        @DecimalMin("1")
        BigDecimal amount,

        @Size(max = 100)
        String orderName
) {
}
