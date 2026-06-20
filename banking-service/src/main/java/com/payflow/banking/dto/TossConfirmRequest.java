package com.payflow.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TossConfirmRequest(
        @NotBlank
        String paymentKey,

        @NotBlank
        String orderId,

        @NotNull
        @DecimalMin("1")
        BigDecimal amount
) {
}
