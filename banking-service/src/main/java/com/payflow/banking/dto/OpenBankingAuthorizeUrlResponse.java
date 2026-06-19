package com.payflow.banking.dto;

public record OpenBankingAuthorizeUrlResponse(
        String authorizeUrl,
        String state
) {
}
