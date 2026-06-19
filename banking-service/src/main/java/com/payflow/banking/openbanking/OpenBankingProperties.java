package com.payflow.banking.openbanking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbanking")
public record OpenBankingProperties(
        String mode,
        String baseUrl,
        String clientId,
        String clientSecret,
        String callbackUrl,
        String clientUseCode,
        String accessToken,
        String userAccessToken,
        String orgAccessToken,
        String cntrAccountType,
        String cntrAccountNum,
        String wdPassPhrase,
        String chargeDpsPrintContent,
        String chargeWdPrintContent,
        String withdrawalWdPrintContent,
        String transferPurpose,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
    public String effectiveOrgAccessToken() {
        return hasText(orgAccessToken) ? orgAccessToken : accessToken;
    }

    public String effectiveUserAccessToken() {
        return hasText(userAccessToken) ? userAccessToken : accessToken;
    }

    public String effectiveCntrAccountType() {
        return hasText(cntrAccountType) ? cntrAccountType : "N";
    }

    public String effectiveWdPassPhrase() {
        return hasText(wdPassPhrase) ? wdPassPhrase : "NONE";
    }

    public String effectiveTransferPurpose() {
        return hasText(transferPurpose) ? transferPurpose : "TR";
    }

    public int effectiveConnectTimeoutMs() {
        return connectTimeoutMs == null ? 3000 : connectTimeoutMs;
    }

    public int effectiveReadTimeoutMs() {
        return readTimeoutMs == null ? 5000 : readTimeoutMs;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
