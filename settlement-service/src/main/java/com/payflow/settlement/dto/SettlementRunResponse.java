package com.payflow.settlement.dto;

import com.payflow.settlement.entity.SettlementRun;
import com.payflow.settlement.entity.SettlementRunStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementRunResponse(
        Long id, LocalDate businessDate, SettlementRunStatus status, long transactionCount,
        long discrepancyCount, BigDecimal grossAmount, BigDecimal cancelAmount,
        BigDecimal feeAmount, BigDecimal expectedNetAmount, LocalDateTime completedAt
) {
    public static SettlementRunResponse from(SettlementRun run) {
        return new SettlementRunResponse(run.getId(), run.getBusinessDate(), run.getStatus(), run.getTransactionCount(),
                run.getDiscrepancyCount(), run.getGrossAmount(), run.getCancelAmount(), run.getFeeAmount(),
                run.getExpectedNetAmount(), run.getCompletedAt());
    }
}
