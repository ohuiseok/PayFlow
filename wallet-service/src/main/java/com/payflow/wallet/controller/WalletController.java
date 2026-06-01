package com.payflow.wallet.controller;

import com.payflow.wallet.dto.CreateWalletRequest;
import com.payflow.wallet.dto.WalletBalanceChangeRequest;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.service.WalletService;
import com.payflow.wallet.support.error.BusinessException;
import com.payflow.wallet.support.error.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(
            @Valid @RequestBody CreateWalletRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return walletService.createWallet(request, requestUserId);
    }

    @GetMapping("/{walletId}")
    public WalletResponse getWallet(
            @PathVariable Long walletId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return walletService.getWallet(walletId, requestUserId);
    }

    @PostMapping("/{walletId}/deposit")
    public WalletResponse deposit(
            @PathVariable Long walletId,
            @Valid @RequestBody WalletBalanceChangeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long requestUserId,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest
    ) {
        return walletService.deposit(walletId, request, requestUserId, internalRequest);
    }

    @PostMapping("/{walletId}/withdraw")
    public WalletResponse withdraw(
            @PathVariable Long walletId,
            @Valid @RequestBody WalletBalanceChangeRequest request,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest
    ) {
        if (!internalRequest) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return walletService.withdraw(walletId, request);
    }
}
