package com.payflow.banking.openbanking;

public record OpenBankingTransferResultRequest(
        String checkType,
        String tranDtime,
        String orgBankTranId,
        String orgBankTranDate,
        String orgTranAmt
) {
}
