package com.payflow.user.service;

import com.payflow.user.client.WalletClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * [H-2] 사용자 생성 후 지갑 생성을 재시도하는 서비스다.
 *
 * <p>UserService와 별도 빈으로 분리한 이유: Spring @Retryable은 AOP 프록시로 동작하므로
 * 같은 클래스 내부에서 호출하면 프록시를 거치지 않아 재시도가 실행되지 않는다.
 * 별도 빈으로 분리해야 @Retryable이 올바르게 적용된다.</p>
 *
 * <p>최종 실패 시 @Recover가 호출되어 로그를 남긴다.
 * 운영 환경에서는 이 로그를 기반으로 수동 보정 배치 또는 보상 트랜잭션을 적용할 수 있다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletProvisioningService {

    private final WalletClient walletClient;

    /**
     * 지갑 생성을 최대 3회 시도한다.
     * 1초, 2초, 4초 간격으로 지수 백오프 재시도를 수행한다.
     */
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void createWalletWithRetry(Long userId) {
        walletClient.createWallet(userId);
    }

    /**
     * 3회 재시도 후에도 실패하면 이 메서드가 호출된다.
     * 지갑 생성 실패는 사용자 생성과 원자적으로 묶이지 않으므로, 실패 사유를 남겨 운영자가 수동으로 처리할 수 있게 한다.
     */
    @Recover
    public void recoverWalletCreation(RuntimeException exception, Long userId) {
        log.error("[WalletProvisioning] userId={}에 대한 지갑 생성이 3회 재시도 후 최종 실패했습니다. " +
                "운영자가 수동으로 지갑을 생성해야 합니다. error={}", userId, exception.getMessage());
    }
}
