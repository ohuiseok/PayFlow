package com.payflow.wallet.dto;

import com.payflow.wallet.entity.WalletReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WalletBalanceChangeRequest(
        @NotNull BigDecimal amount,
        @NotNull WalletReferenceType referenceType,
        @NotBlank String referenceId
) {
}
