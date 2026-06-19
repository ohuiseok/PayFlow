package com.payflow.banking.openbanking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBankingTransferResultResponse(
        @JsonProperty("api_tran_id") String apiTranId,
        @JsonProperty("api_tran_dtm") String apiTranDtm,
        @JsonProperty("rsp_code") String rspCode,
        @JsonProperty("rsp_message") String rspMessage,
        @JsonProperty("res_cnt") String resCnt,
        @JsonProperty("res_list") List<ResultItem> resList
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultItem(
            @JsonProperty("tran_no") String tranNo,
            @JsonProperty("bank_tran_id") String bankTranId,
            @JsonProperty("bank_tran_date") String bankTranDate,
            @JsonProperty("bank_code_tran") String bankCodeTran,
            @JsonProperty("bank_rsp_code") String bankRspCode,
            @JsonProperty("bank_rsp_message") String bankRspMessage,
            @JsonProperty("tran_amt") String tranAmt
    ) {
    }
}
