package com.payflow.transfer.monitor;

import com.payflow.transfer.entity.Transfer;
import com.payflow.transfer.entity.TransferStatus;
import com.payflow.transfer.repository.TransferRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * [M-6] PROCESSING 상태가 30분 이상 지속되는 고착 송금을 감지해 경고 로그를 남긴다.
 *
 * <p>왜 고착이 발생하는가:
 * 송금은 출금(wallet-service) → Kafka 이벤트 발행(ledger-service) → 입금(wallet-service) 순서로 진행된다.
 * 이 과정에서 Kafka 브로커 장애, 네트워크 단절, 컨슈머 재시작 등이 발생하면
 * Transfer 엔티티가 PROCESSING 상태에서 벗어나지 못하고 고착될 수 있다.</p>
 *
 * <p>이 스케줄러는 직접 상태를 변경하지 않는다. 자동 복구는 오히려 데이터 불일치를 유발할 수 있으므로
 * 운영자가 로그를 확인하고 보상 트랜잭션(/transfers/compensations) API로 수동 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckTransferMonitor {

    private static final long STUCK_THRESHOLD_MINUTES = 30L;

    private final TransferRepository transferRepository;

    /**
     * 5분마다 실행해 고착 송금을 감지한다.
     * fixedDelay이 아닌 cron을 사용해 인스턴스 재시작 직후 불필요한 스캔을 막는다.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void detectStuckTransfers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<Transfer> stuckTransfers = transferRepository.findByStatusAndCreatedAtBefore(
                TransferStatus.PROCESSING,
                threshold
        );

        if (stuckTransfers.isEmpty()) {
            return;
        }

        // 한 번의 감지 사이클에서 찾은 건수를 먼저 집계 로그로 남긴다.
        log.warn("[StuckTransfer] {}분 이상 PROCESSING 상태인 송금 {}건 감지됨", STUCK_THRESHOLD_MINUTES, stuckTransfers.size());

        // 건별로 상세 정보를 남겨 운영자가 직접 보상 처리할 수 있도록 한다.
        for (Transfer transfer : stuckTransfers) {
            log.warn("[StuckTransfer] transferId={}, senderUserId={}, receiverUserId={}, amount={}, createdAt={}",
                    transfer.getId(),
                    transfer.getSenderUserId(),
                    transfer.getReceiverUserId(),
                    transfer.getAmount(),
                    transfer.getCreatedAt()
            );
        }
    }
}
