package com.payflow.transfer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "wallet-service", url = "${clients.wallet-service.url:http://localhost:8082}")
public interface WalletClient {

    @GetMapping("/wallets/users/{userId}")
    WalletResponse getWalletByUserId(
            @PathVariable Long userId,
            @RequestHeader("X-Internal-Request") boolean internalRequest,
            @RequestHeader("X-Internal-Secret") String internalSecret
    );

    @PostMapping("/wallets/{walletId}/withdraw")
    WalletResponse withdraw(
            @PathVariable Long walletId,
            @RequestBody WalletBalanceChangeRequest request,
            @RequestHeader("X-Internal-Request") boolean internalRequest,
            @RequestHeader("X-Internal-Secret") String internalSecret
    );

    @PostMapping("/wallets/{walletId}/deposit")
    WalletResponse deposit(
            @PathVariable Long walletId,
            @RequestBody WalletBalanceChangeRequest request,
            @RequestHeader("X-Internal-Request") boolean internalRequest,
            @RequestHeader("X-Internal-Secret") String internalSecret
    );
}
