package com.payflow.reward.dto;

import java.math.BigDecimal;

public record ParentCreditSummaryResponse(
        Long walletId,
        BigDecimal creditBalance,
        BigDecimal monthlyRewardPaid,
        long pendingApprovalCount
) {
}
