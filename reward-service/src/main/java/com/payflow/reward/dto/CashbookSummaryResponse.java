package com.payflow.reward.dto;

import java.math.BigDecimal;

public record CashbookSummaryResponse(
        Long childUserId,
        BigDecimal walletBalance,
        BigDecimal paidRewardAmount,
        long paidMissionCount
) {
}
