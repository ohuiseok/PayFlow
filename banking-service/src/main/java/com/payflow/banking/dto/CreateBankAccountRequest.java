package com.payflow.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBankAccountRequest(
        @NotBlank @Size(max = 20) String bankCode,
        @NotBlank @Size(max = 80) String accountNumber,
        @NotBlank @Size(max = 100) String accountHolderName,
        @Size(max = 30) String fintechUseNum,
        @Size(max = 20) String userSeqNo,
        @Size(max = 100) String bankName,
        @Size(max = 5) String inquiryAgreeYn,
        @Size(max = 5) String transferAgreeYn
) {
    public CreateBankAccountRequest(String bankCode, String accountNumber, String accountHolderName) {
        this(bankCode, accountNumber, accountHolderName, null, null, null, null, null);
    }
}
