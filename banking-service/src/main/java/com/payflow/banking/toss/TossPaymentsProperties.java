package com.payflow.banking.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss-payments")
public record TossPaymentsProperties(
        String mode,
        String apiBaseUrl,
        String clientKey,
        String secretKey,
        String webhookSecret
) {

    public String effectiveMode() {
        return mode == null || mode.isBlank() ? "mock" : mode.trim();
    }

    public String effectiveApiBaseUrl() {
        return apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.tosspayments.com" : apiBaseUrl.trim();
    }
}
