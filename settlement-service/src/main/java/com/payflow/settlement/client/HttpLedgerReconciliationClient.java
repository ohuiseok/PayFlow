package com.payflow.settlement.client;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpLedgerReconciliationClient implements LedgerReconciliationClient {
    private final RestClient restClient;

    public HttpLedgerReconciliationClient(
            @Value("${clients.ledger-service.url:http://localhost:8084}") String baseUrl,
            @Value("${internal.secret:}") String internalSecret
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", internalSecret)
                .build();
    }

    @Override
    public Optional<LedgerEntrySnapshot> findPaymentEntry(String sourceType, Long sourceId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uri -> uri.path("/ledgers/internal/payment-entry")
                            .queryParam("sourceType", sourceType).queryParam("sourceId", sourceId).build())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve().body(LedgerEntrySnapshot.class));
        } catch (HttpClientErrorException.NotFound ignored) {
            return Optional.empty();
        }
    }
}
