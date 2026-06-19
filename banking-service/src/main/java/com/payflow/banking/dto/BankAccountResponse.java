package com.payflow.banking.dto;

import com.payflow.banking.entity.BankAccount;
import com.payflow.banking.entity.BankAccountStatus;

public record BankAccountResponse(
        Long bankAccountId,
        String bankCode,
        String accountNumberMasked,
        String accountHolderName,
        String bankName,
        String inquiryAgreeYn,
        String transferAgreeYn,
        BankAccountStatus status
) {

    public static BankAccountResponse from(BankAccount account) {
        return new BankAccountResponse(
                account.getId(),
                account.getBankCode(),
                account.getAccountNumberMasked(),
                account.getAccountHolderName(),
                account.getBankName(),
                account.getInquiryAgreeYn(),
                account.getTransferAgreeYn(),
                account.getStatus()
        );
    }
}
