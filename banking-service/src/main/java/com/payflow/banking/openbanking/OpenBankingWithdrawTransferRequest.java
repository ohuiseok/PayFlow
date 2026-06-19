package com.payflow.banking.openbanking;

public record OpenBankingWithdrawTransferRequest(
        String bankTranId,
        String cntrAccountType,
        String cntrAccountNum,
        String dpsPrintContent,
        String fintechUseNum,
        String wdPrintContent,
        String tranAmt,
        String tranDtime,
        String reqClientName,
        String reqClientFintechUseNum,
        String reqClientNum,
        String transferPurpose
) {
}
