package com.payflow.banking.openbanking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "openbanking", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockOpenBankingClient implements OpenBankingClient {

    @Override
    public OpenBankingTokenResponse issueOrgToken() {
        return new OpenBankingTokenResponse("mock-token", null, "Bearer", 3600L, "oob", "MOCK000000", null);
    }

    @Override
    public OpenBankingTokenResponse exchangeAuthorizationCode(String code) {
        return new OpenBankingTokenResponse("mock-user-token", "mock-refresh-token", "Bearer", 3600L, "login inquiry transfer", null, "mock-user-seq");
    }

    @Override
    public OpenBankingUserMeResponse getUserMe(String userSeqNo) {
        return getUserMe(userSeqNo, "mock-user-token");
    }

    @Override
    public OpenBankingUserMeResponse getUserMe(String userSeqNo, String userAccessToken) {
        return new OpenBankingUserMeResponse(
                "mock-api-tran-id",
                null,
                "A0000",
                "mock success",
                userSeqNo,
                "mock-user",
                "1",
                java.util.List.of(new OpenBankingUserMeResponse.Account(
                        "mock-fintech-use-num",
                        "mock-account",
                        "004",
                        "MOCK_BANK",
                        "123-****-7890",
                        "mock-user",
                        "Y",
                        "Y"
                ))
        );
    }

    @Override
    public OpenBankingTransferResponse withdrawTransfer(OpenBankingWithdrawTransferRequest request) {
        return withdrawTransfer(request, "mock-user-token");
    }

    @Override
    public OpenBankingTransferResponse withdrawTransfer(OpenBankingWithdrawTransferRequest request, String userAccessToken) {
        return new OpenBankingTransferResponse(
                "mock-api-tran-id",
                request.tranDtime(),
                "A0000",
                "mock success",
                request.bankTranId(),
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                null,
                "000",
                "mock success",
                request.tranAmt()
        );
    }

    @Override
    public OpenBankingTransferResultResponse transferResult(OpenBankingTransferResultRequest request) {
        return transferResult(request, "mock-org-token");
    }

    @Override
    public OpenBankingTransferResultResponse transferResult(OpenBankingTransferResultRequest request, String orgAccessToken) {
        return new OpenBankingTransferResultResponse(
                "mock-api-tran-id",
                request.tranDtime(),
                "A0000",
                "mock success",
                "1",
                java.util.List.of(new OpenBankingTransferResultResponse.ResultItem(
                        "1",
                        request.orgBankTranId(),
                        request.orgBankTranDate(),
                        null,
                        "000",
                        "mock success",
                        request.orgTranAmt()
                ))
        );
    }

    @Override
    public void attemptRealNameInquiry(OpenBankingRealNameInquiryRequest request) {
    }

    @Override
    public void attemptReceiveInquiry(OpenBankingReceiveInquiryRequest request) {
    }

    @Override
    public void attemptDepositTransfer(OpenBankingDepositTransferRequest request) {
    }
}
