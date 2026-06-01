package com.payflow.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WalletBalanceChangeRequest(
        @NotNull BigDecimal amount,
        @NotBlank String referenceType,
        @NotBlank String referenceId
) {
}
