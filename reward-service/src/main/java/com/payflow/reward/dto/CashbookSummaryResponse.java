package com.payflow.reward.dto;

import java.math.BigDecimal;

public record CashbookSummaryResponse(
        Long childUserId,
        Long walletId,
        BigDecimal walletBalance,
        BigDecimal paidRewardAmount,
        long paidMissionCount
) {
}
