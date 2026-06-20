package com.payflow.banking.dto;

public record TossWebhookResponse(
        boolean received,
        boolean duplicate
) {
}
