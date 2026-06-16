package com.payflow.wallet.controller;

import com.payflow.wallet.dto.CreateWalletRequest;
import com.payflow.wallet.dto.WalletBalanceChangeRequest;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.service.WalletService;
import com.payflow.wallet.support.error.BusinessException;
import com.payflow.wallet.support.error.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
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

    @Value("${internal.secret:}")
    private String internalSecret;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(
            @Valid @RequestBody CreateWalletRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        // X-User-Id는 게이트웨이가 JWT에서 꺼내 넣어 준 값이다.
        // body의 userId와 비교해 다른 사람 명의의 지갑 생성을 막는다.
        return walletService.createWallet(request, requestUserId);
    }

    @GetMapping("/{walletId}")
    public WalletResponse getWallet(
            @PathVariable Long walletId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return walletService.getWallet(walletId, requestUserId);
    }

    @GetMapping("/users/{userId}")
    public WalletResponse getWalletByUserId(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        // userId로 지갑을 찾는 API는 transfer-service 같은 내부 서비스용이다.
        // 외부 사용자는 walletId 기반 조회만 사용하고, 이 경로는 내부 secret을 요구한다.
        if (!internalRequest) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validateInternalRequest(true, requestInternalSecret);
        return walletService.getWalletByUserId(userId);
    }

    @PostMapping("/{walletId}/deposit")
    public WalletResponse deposit(
            @PathVariable Long walletId,
            @Valid @RequestBody WalletBalanceChangeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long requestUserId,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        // 입금은 외부 사용자 요청과 내부 서비스 요청을 모두 받을 수 있다.
        // 내부 요청이면 secret을 검증하고, 외부 요청이면 서비스 계층에서 지갑 소유권을 검증한다.
        validateInternalRequest(internalRequest, requestInternalSecret);
        return walletService.deposit(walletId, request, requestUserId, internalRequest);
    }

    @PostMapping("/{walletId}/withdraw")
    public WalletResponse withdraw(
            @PathVariable Long walletId,
            @Valid @RequestBody WalletBalanceChangeRequest request,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        // 출금은 돈이 빠져나가는 동작이라 현재 내부 서비스만 허용한다.
        // 사용자가 직접 호출하는 출금 API가 필요하면 별도 인증/승인 흐름을 설계해야 한다.
        if (!internalRequest) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validateInternalRequest(true, requestInternalSecret);
        return walletService.withdraw(walletId, request);
    }

    private void validateInternalRequest(boolean internalRequest, String requestInternalSecret) {
        if (!internalRequest) {
            return;
        }
        // X-Internal-Request 헤더만으로는 내부 호출을 신뢰할 수 없다.
        // 공유 secret까지 일치해야 서비스 간 호출로 인정한다.
        if (!StringUtils.hasText(internalSecret) || !internalSecret.equals(requestInternalSecret)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
