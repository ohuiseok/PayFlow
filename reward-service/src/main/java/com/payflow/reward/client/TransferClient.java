package com.payflow.reward.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "transfer-service", url = "${clients.transfer-service.url:http://localhost:8083}")
public interface TransferClient {

    @PostMapping("/transfers")
    TransferResponse createTransfer(
            @RequestBody CreateTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    );
}
