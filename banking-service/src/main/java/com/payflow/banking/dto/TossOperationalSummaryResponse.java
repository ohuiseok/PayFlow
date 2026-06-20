package com.payflow.banking.dto;

public record TossOperationalSummaryResponse(
        long readyCount,
        long completedCount,
        long failedCount,
        long canceledCount,
        long compensationRequiredCount,
        long ledgerCompensationRequiredCount
) {
}
