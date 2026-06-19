package com.payflow.banking.openbanking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "openbanking", name = "mode", havingValue = "real")
public class RealOpenBankingClient implements OpenBankingClient {

    private final RestClient restClient;
    private final OpenBankingProperties properties;

    public RealOpenBankingClient(OpenBankingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.effectiveConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.effectiveReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public OpenBankingTokenResponse issueOrgToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", "oob");
        form.add("grant_type", "client_credentials");
        return postForm("/oauth/2.0/token", form, OpenBankingTokenResponse.class);
    }

    @Override
    public OpenBankingTokenResponse exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.callbackUrl());
        form.add("grant_type", "authorization_code");
        return postForm("/oauth/2.0/token", form, OpenBankingTokenResponse.class);
    }

    @Override
    public OpenBankingUserMeResponse getUserMe(String userSeqNo) {
        return getUserMe(userSeqNo, properties.effectiveUserAccessToken());
    }

    @Override
    public OpenBankingUserMeResponse getUserMe(String userSeqNo, String userAccessToken) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2.0/user/me")
                        .queryParam("user_seq_no", userSeqNo)
                        .build())
                .header("Authorization", bearer(userAccessToken))
                .retrieve()
                .body(OpenBankingUserMeResponse.class);
    }

    @Override
    public OpenBankingTransferResponse withdrawTransfer(OpenBankingWithdrawTransferRequest request) {
        return withdrawTransfer(request, properties.effectiveUserAccessToken());
    }

    @Override
    public OpenBankingTransferResponse withdrawTransfer(OpenBankingWithdrawTransferRequest request, String userAccessToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bank_tran_id", request.bankTranId());
        body.put("cntr_account_type", request.cntrAccountType());
        body.put("cntr_account_num", request.cntrAccountNum());
        body.put("dps_print_content", request.dpsPrintContent());
        body.put("fintech_use_num", request.fintechUseNum());
        body.put("wd_print_content", request.wdPrintContent());
        body.put("tran_amt", request.tranAmt());
        body.put("tran_dtime", request.tranDtime());
        body.put("req_client_name", request.reqClientName());
        body.put("req_client_fintech_use_num", request.reqClientFintechUseNum());
        body.put("req_client_num", request.reqClientNum());
        body.put("transfer_purpose", request.transferPurpose());
        return postJson(
                "/v2.0/transfer/withdraw/fin_num",
                bearer(userAccessToken),
                body,
                OpenBankingTransferResponse.class
        );
    }

    @Override
    public OpenBankingTransferResultResponse transferResult(OpenBankingTransferResultRequest request) {
        return transferResult(request, properties.effectiveOrgAccessToken());
    }

    @Override
    public OpenBankingTransferResultResponse transferResult(OpenBankingTransferResultRequest request, String orgAccessToken) {
        return postJson(
                "/v2.0/transfer/result",
                bearer(orgAccessToken),
                Map.of(
                        "check_type", request.checkType(),
                        "tran_dtime", request.tranDtime(),
                        "req_cnt", "1",
                        "req_list", List.of(Map.of(
                                "tran_no", "1",
                                "org_bank_tran_id", request.orgBankTranId(),
                                "org_bank_tran_date", request.orgBankTranDate(),
                                "org_tran_amt", request.orgTranAmt()
                        ))
                ),
                OpenBankingTransferResultResponse.class
        );
    }

    @Override
    public void attemptRealNameInquiry(OpenBankingRealNameInquiryRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bank_tran_id", request.bankTranId());
        body.put("bank_code_std", request.bankCodeStd());
        body.put("account_num", request.accountNum());
        body.put("account_holder_info_type", request.accountHolderInfoType());
        body.put("account_holder_info", request.accountHolderInfo());
        body.put("tran_dtime", request.tranDtime());
        postJson(
                "/v2.0/inquiry/real_name",
                bearer(properties.effectiveOrgAccessToken()),
                body,
                Map.class
        );
    }

    @Override
    public void attemptReceiveInquiry(OpenBankingReceiveInquiryRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bank_tran_id", request.bankTranId());
        body.put("cntr_account_type", request.cntrAccountType());
        body.put("cntr_account_num", request.cntrAccountNum());
        body.put("bank_code_std", request.bankCodeStd());
        body.put("account_num", request.accountNum());
        body.put("account_holder_name", request.accountHolderName());
        body.put("print_content", request.printContent());
        body.put("req_client_name", request.reqClientName());
        body.put("req_client_num", request.reqClientNum());
        body.put("req_client_fintech_use_num", request.reqClientFintechUseNum());
        body.put("transfer_purpose", request.transferPurpose());
        body.put("tran_amt", request.tranAmt());
        body.put("cms_num", request.cmsNum());
        body.put("tran_dtime", request.tranDtime());
        postJson(
                "/v2.0/inquiry/receive",
                bearer(properties.effectiveOrgAccessToken()),
                body,
                Map.class
        );
    }

    @Override
    public void attemptDepositTransfer(OpenBankingDepositTransferRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tran_no", "1");
        item.put("bank_tran_id", request.bankTranId());
        item.put("fintech_use_num", request.fintechUseNum());
        item.put("print_content", request.printContent());
        item.put("tran_amt", request.tranAmt());
        item.put("req_client_name", request.reqClientName());
        item.put("req_client_fintech_use_num", request.reqClientFintechUseNum());
        item.put("req_client_num", request.reqClientNum());
        item.put("transfer_purpose", request.transferPurpose());
        item.put("cms_num", request.cmsNum());
        item.put("withdraw_bank_tran_id", request.withdrawBankTranId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cntr_account_type", request.cntrAccountType());
        body.put("cntr_account_num", request.cntrAccountNum());
        body.put("wd_pass_phrase", request.wdPassPhrase());
        body.put("wd_print_content", request.wdPrintContent());
        body.put("name_check_option", request.nameCheckOption());
        body.put("tran_dtime", request.tranDtime());
        body.put("req_cnt", "1");
        body.put("req_list", List.of(item));

        postJson(
                "/v2.0/transfer/deposit/fin_num",
                bearer(properties.effectiveOrgAccessToken()),
                body,
                Map.class
        );
    }

    private <T> T postJson(String path, String authorization, Map<String, ?> body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (ResourceAccessException exception) {
            throw new OpenBankingAmbiguousException("OpenBanking request did not return a clear response.", exception);
        }
    }

    private <T> T postForm(String path, MultiValueMap<String, String> form, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(responseType);
        } catch (ResourceAccessException exception) {
            throw new OpenBankingAmbiguousException("OpenBanking request did not return a clear response.", exception);
        }
    }

    private String bearer(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("OpenBanking access token is required for real mode");
        }
        return "Bearer " + token;
    }
}
