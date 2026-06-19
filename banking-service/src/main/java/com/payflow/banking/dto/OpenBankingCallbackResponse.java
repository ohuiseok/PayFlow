package com.payflow.banking.dto;

import java.util.List;

public record OpenBankingCallbackResponse(
        String userSeqNo,
        String scope,
        List<BankAccountResponse> accounts
) {
}
