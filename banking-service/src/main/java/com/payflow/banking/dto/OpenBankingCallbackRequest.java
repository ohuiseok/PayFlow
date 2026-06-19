package com.payflow.banking.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenBankingCallbackRequest(
        @NotBlank String code,
        @NotBlank String state
) {
}
