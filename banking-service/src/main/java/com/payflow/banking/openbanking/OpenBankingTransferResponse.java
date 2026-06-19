package com.payflow.banking.openbanking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBankingTransferResponse(
        @JsonProperty("api_tran_id") String apiTranId,
        @JsonProperty("api_tran_dtm") String apiTranDtm,
        @JsonProperty("rsp_code") String rspCode,
        @JsonProperty("rsp_message") String rspMessage,
        @JsonProperty("bank_tran_id") String bankTranId,
        @JsonProperty("bank_tran_date") String bankTranDate,
        @JsonProperty("bank_code_tran") String bankCodeTran,
        @JsonProperty("bank_rsp_code") String bankRspCode,
        @JsonProperty("bank_rsp_message") String bankRspMessage,
        @JsonProperty("tran_amt") String tranAmt
) {
    public boolean isSuccess() {
        return "A0000".equals(rspCode) && "000".equals(bankRspCode);
    }

    public boolean needsResultCheck() {
        return "A0001".equals(rspCode)
                || "A0007".equals(rspCode)
                || "400".equals(bankRspCode)
                || "803".equals(bankRspCode)
                || "804".equals(bankRspCode)
                || "819".equals(bankRspCode)
                || "822".equals(bankRspCode);
    }

    public String summary() {
        return "rsp_code=" + rspCode + ", bank_rsp_code=" + bankRspCode;
    }
}
