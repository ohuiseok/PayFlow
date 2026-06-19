package com.payflow.banking.openbanking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBankingUserMeResponse(
        @JsonProperty("api_tran_id") String apiTranId,
        @JsonProperty("api_tran_dtm") String apiTranDtm,
        @JsonProperty("rsp_code") String rspCode,
        @JsonProperty("rsp_message") String rspMessage,
        @JsonProperty("user_seq_no") String userSeqNo,
        @JsonProperty("user_name") String userName,
        @JsonProperty("res_cnt") String resCnt,
        @JsonProperty("res_list") List<Account> resList
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
            @JsonProperty("fintech_use_num") String fintechUseNum,
            @JsonProperty("account_alias") String accountAlias,
            @JsonProperty("bank_code_std") String bankCodeStd,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_num_masked") String accountNumMasked,
            @JsonProperty("account_holder_name") String accountHolderName,
            @JsonProperty("inquiry_agree_yn") String inquiryAgreeYn,
            @JsonProperty("transfer_agree_yn") String transferAgreeYn
    ) {
    }
}
