package com.payflow.banking.support.error;

public record ErrorResponse(
        String code,
        String message,
        String traceId
) {
}
