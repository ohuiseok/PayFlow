package com.payflow.banking.openbanking;

public record OpenBankingDepositTransferRequest(
        String cntrAccountType,
        String cntrAccountNum,
        String wdPassPhrase,
        String wdPrintContent,
        String nameCheckOption,
        String tranDtime,
        String bankTranId,
        String fintechUseNum,
        String printContent,
        String tranAmt,
        String reqClientName,
        String reqClientFintechUseNum,
        String reqClientNum,
        String transferPurpose,
        String cmsNum,
        String withdrawBankTranId
) {
}
