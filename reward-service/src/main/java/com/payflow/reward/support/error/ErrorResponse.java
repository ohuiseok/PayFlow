package com.payflow.reward.support.error;

public record ErrorResponse(
        String code,
        String message,
        String traceId
) {
}
