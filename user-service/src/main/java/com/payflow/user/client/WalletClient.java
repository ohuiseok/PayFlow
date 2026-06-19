package com.payflow.user.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {

    @Value("${clients.wallet-service.url:http://localhost:8082}")
    private String walletServiceUrl;

    @Value("${clients.wallet-service.provisioning-enabled:true}")
    private boolean provisioningEnabled;

    @Value("${internal.secret:}")
    private String internalSecret;

    public void createWallet(Long userId) {
        if (!provisioningEnabled) {
            return;
        }

        try {
            RestClient
                    .builder()
                    .baseUrl(walletServiceUrl)
                    .build()
                    .post()
                    .uri("/wallets")
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Internal-Secret", internalSecret)
                    .body(new CreateWalletRequest(userId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                return;
            }
            throw exception;
        }
    }

    private record CreateWalletRequest(Long userId) {
    }
}
