package com.payflow.wallet.dto;

import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(
        @NotNull Long userId
) {
}
