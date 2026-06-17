package com.payflow.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBankAccountRequest(
        @NotBlank @Size(max = 20) String bankCode,
        @NotBlank @Size(max = 80) String accountNumber,
        @NotBlank @Size(max = 100) String accountHolderName
) {
}
