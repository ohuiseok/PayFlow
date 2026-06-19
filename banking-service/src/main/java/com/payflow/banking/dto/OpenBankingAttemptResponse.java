package com.payflow.banking.dto;

public record OpenBankingAttemptResponse(
        String apiName,
        boolean attempted
) {
}
