package com.payflow.banking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "ledger-service", url = "${clients.ledger-service.url:http://localhost:8084}")
public interface LedgerClient {

    @PostMapping("/ledgers/internal/payment-charge")
    void recordPaymentCharge(
            @RequestBody LedgerPaymentRecordRequest request,
            @RequestHeader("X-Internal-Request") boolean internalRequest,
            @RequestHeader("X-Internal-Secret") String internalSecret
    );
}
