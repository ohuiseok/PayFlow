package com.payflow.banking.openbanking;

public interface OpenBankingClient {

    OpenBankingTokenResponse issueOrgToken();

    OpenBankingTokenResponse exchangeAuthorizationCode(String code);

    OpenBankingUserMeResponse getUserMe(String userSeqNo);

    OpenBankingUserMeResponse getUserMe(String userSeqNo, String userAccessToken);

    OpenBankingTransferResponse withdrawTransfer(OpenBankingWithdrawTransferRequest request);

    OpenBankingTransferResponse withdrawTransfer(OpenBankingWithdrawTransferRequest request, String userAccessToken);

    OpenBankingTransferResultResponse transferResult(OpenBankingTransferResultRequest request);

    OpenBankingTransferResultResponse transferResult(OpenBankingTransferResultRequest request, String orgAccessToken);

    void attemptRealNameInquiry(OpenBankingRealNameInquiryRequest request);

    void attemptReceiveInquiry(OpenBankingReceiveInquiryRequest request);

    void attemptDepositTransfer(OpenBankingDepositTransferRequest request);
}
