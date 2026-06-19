package com.payflow.banking.openbanking;

public record OpenBankingReceiveInquiryRequest(
        String bankTranId,
        String cntrAccountType,
        String cntrAccountNum,
        String bankCodeStd,
        String accountNum,
        String accountHolderName,
        String printContent,
        String reqClientName,
        String reqClientNum,
        String reqClientFintechUseNum,
        String transferPurpose,
        String tranAmt,
        String cmsNum,
        String tranDtime
) {
}
