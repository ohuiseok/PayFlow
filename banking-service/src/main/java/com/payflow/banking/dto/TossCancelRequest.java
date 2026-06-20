package com.payflow.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record TossCancelRequest(
        @NotBlank
        String cancelReason,

        @DecimalMin("1")
        BigDecimal cancelAmount
) {
}
