package com.payflow.banking.openbanking;

public record OpenBankingRealNameInquiryRequest(
        String bankTranId,
        String bankCodeStd,
        String accountNum,
        String accountHolderInfoType,
        String accountHolderInfo,
        String tranDtime
) {
}
