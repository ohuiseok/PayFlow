package com.payflow.user.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BankingClient {

    @Value("${clients.banking-service.url:http://localhost:8086}")
    private String bankingServiceUrl;

    @Value("${internal.secret:}")
    private String internalSecret;

    public boolean hasActiveBankAccount(Long userId) {
        try {
            var response = RestClient
                    .builder()
                    .baseUrl(bankingServiceUrl)
                    .build()
                    .get()
                    .uri("/bank/internal/has-account")
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Internal-Request", "true")
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .body(HasAccountResponse.class);
            return response != null && Boolean.TRUE.equals(response.hasBankAccount());
        } catch (Exception e) {
            return false;
        }
    }

    private record HasAccountResponse(Boolean hasBankAccount) {}
}
