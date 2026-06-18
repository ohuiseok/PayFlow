package com.payflow.transfer.service;

import com.payflow.transfer.client.WalletBalanceChangeRequest;
import com.payflow.transfer.client.WalletClient;
import com.payflow.transfer.dto.CreateTransferRequest;
import com.payflow.transfer.dto.TransferResponse;
import com.payflow.transfer.entity.Transfer;
import com.payflow.transfer.entity.TransferStatus;
import com.payflow.transfer.event.TransferEventPublisher;
import com.payflow.transfer.lock.DistributedLock;
import com.payflow.transfer.repository.TransferRepository;
import com.payflow.transfer.support.error.BusinessException;
import com.payflow.transfer.support.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TransferService {

    // 송금 금액도 지갑과 같은 정책을 사용한다.
    // 서비스별로 검증을 두면 잘못된 요청이 하위 서비스까지 내려가기 전에 차단된다.
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    // wallet-service에 남길 referenceType이다. 송금 1건이 지갑 거래 2건(출금/입금)의 원인이 된다.
    private static final String TRANSFER_REFERENCE_TYPE = "TRANSFER";
    private static final String TRANSFER_COMPENSATION_REFERENCE_TYPE = "TRANSFER_COMPENSATION";

    private final TransferRepository transferRepository;
    private final WalletClient walletClient;
    private final TransferEventPublisher transferEventPublisher;
    private final DistributedLock distributedLock;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Value("${transfer.wallet-lock.ttl:5s}")
    private Duration walletLockTtl;

    @Transactional
    public TransferResponse createTransfer(CreateTransferRequest request, String idempotencyKey, Long senderUserId) {
        // Idempotency-Key는 클라이언트가 같은 요청을 재시도할 때 같은 결과를 받기 위한 키다.
        // 네트워크 타임아웃 후 재전송되어도 송금이 두 번 나가지 않게 하는 핵심 장치다.
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        BigDecimal amount = normalizeAmount(request.amount());
        if (senderUserId.equals(request.receiverUserId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "sender and receiver must be different");
        }

        // 같은 멱등키라도 요청 본문이 달라지면 위험하다.
        // 그래서 sender/receiver/amount를 해시로 저장해 "같은 키 + 같은 요청"인지 확인한다.
        String requestHash = createRequestHash(senderUserId, request.receiverUserId(), amount);
        return transferRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> resolveExistingTransfer(existing, requestHash))
                .orElseGet(() -> processNewTransfer(senderUserId, request.receiverUserId(), amount, normalizedIdempotencyKey, requestHash));
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long transferId, Long requestUserId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
        validateParticipant(transfer, requestUserId);
        return TransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getTransfers(Long requestUserId) {
        return transferRepository.findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(requestUserId, requestUserId)
                .stream()
                .map(TransferResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getCompensations() {
        return transferRepository.findByStatusOrderByCreatedAtDesc(TransferStatus.COMPENSATION_REQUIRED)
                .stream()
                .map(TransferResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse getCompensation(Long transferId) {
        return transferRepository.findByIdAndStatus(transferId, TransferStatus.COMPENSATION_REQUIRED)
                .map(TransferResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
    }

    @Transactional
    public TransferResponse refundCompensation(Long transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
        if (transfer.getStatus() == TransferStatus.COMPENSATED) {
            return TransferResponse.from(transfer);
        }
        if (transfer.getStatus() != TransferStatus.COMPENSATION_REQUIRED || transfer.getSenderWalletId() == null) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER_STATUS);
        }

        walletClient.deposit(
                transfer.getSenderWalletId(),
                new WalletBalanceChangeRequest(
                        transfer.getAmount(),
                        TRANSFER_COMPENSATION_REFERENCE_TYPE,
                        transfer.getId().toString()
                ),
                true,
                internalSecret
        );
        transfer.compensate();
        return TransferResponse.from(transfer);
    }

    private TransferResponse resolveExistingTransfer(Transfer transfer, String requestHash) {
        // 같은 Idempotency-Key로 다른 금액/수신자 요청이 들어오면 기존 결과를 주면 안 된다.
        // 클라이언트 버그나 키 재사용을 명확히 드러내기 위해 409 계열 에러로 처리한다.
        if (!transfer.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        return TransferResponse.from(transfer);
    }

    private TransferResponse processNewTransfer(
            Long senderUserId,
            Long receiverUserId,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash
    ) {
        // 먼저 송금 요청 자체를 DB에 남긴다.
        // 이후 외부 호출이 실패하더라도 어떤 요청이 어떤 상태로 끝났는지 조회할 수 있어야 장애 분석이 가능하다.
        Transfer transfer = transferRepository.saveAndFlush(new Transfer(senderUserId, receiverUserId, amount, idempotencyKey, requestHash));
        try {
            // userId 기준으로 각 사용자의 지갑을 조회한다. transfer-service는 지갑 DB를 직접 보지 않고 wallet-service API만 사용한다.
            // 이 경계를 지켜야 지갑 잔액의 진실(source of truth)이 wallet-service 하나로 유지된다.
            var senderWallet = walletClient.getWalletByUserId(senderUserId, true, internalSecret);
            var receiverWallet = walletClient.getWalletByUserId(receiverUserId, true, internalSecret);
            String lockKey = "transfer:wallet-lock:" + senderWallet.walletId();
            String ownerToken = UUID.randomUUID().toString();
            if (!distributedLock.tryLock(lockKey, ownerToken, walletLockTtl)) {
                throw new BusinessException(ErrorCode.WALLET_LOCK_CONFLICT);
            }

            try {
            // PROCESSING 상태와 실제 walletId를 저장한 뒤 돈을 움직인다.
            // 상태 전이를 남겨 두면 중간 장애가 났을 때 어디까지 진행됐는지 복구 판단이 가능하다.
            transfer.start(senderWallet.walletId(), receiverWallet.walletId());
            String referenceId = transfer.getId().toString();
            // 먼저 보내는 사람 지갑에서 출금한다.
            // referenceId를 송금 ID로 고정하면 같은 송금 재시도에서도 wallet-service가 중복 차감을 막아 준다.
            walletClient.withdraw(
                    senderWallet.walletId(),
                    new WalletBalanceChangeRequest(amount, TRANSFER_REFERENCE_TYPE, referenceId),
                    true,
                    internalSecret
            );
            try {
                // 다음으로 받는 사람 지갑에 입금한다.
                // 출금은 성공했는데 입금이 실패하면 사람의 돈이 중간에 멈춘 상태가 되므로 보상 처리가 필요하다.
                walletClient.deposit(
                        receiverWallet.walletId(),
                        new WalletBalanceChangeRequest(amount, TRANSFER_REFERENCE_TYPE, referenceId),
                        true,
                        internalSecret
                );
            } catch (RuntimeException exception) {
                // 이 상태는 "출금 성공, 입금 실패 가능성"을 의미한다.
                // 자동 환불/재시도 배치가 생기기 전까지 사람이 확인해야 하므로 COMPENSATION_REQUIRED로 남긴다.
                transfer.requireCompensation(resolveMessage(exception));
                transferEventPublisher.publishFailed(transfer);
                return TransferResponse.from(transfer);
            }

            // 두 지갑 반영이 모두 끝나면 성공 이벤트를 발행한다.
            // ledger-service는 이 이벤트를 소비해 복식부기 원장 기록을 만든다.
            transfer.succeed();
            transferEventPublisher.publishCompleted(transfer);
            return TransferResponse.from(transfer);
            } finally {
                releaseLockQuietly(lockKey, ownerToken);
            }
        } catch (RuntimeException exception) {
            // 지갑 조회나 출금 단계에서 실패한 경우다.
            // 돈이 움직이기 전 실패일 수 있으므로 일반 FAILED로 기록하고 실패 이벤트를 발행한다.
            transfer.fail(resolveMessage(exception));
            transferEventPublisher.publishFailed(transfer);
            return TransferResponse.from(transfer);
        }
    }

    private void releaseLockQuietly(String lockKey, String ownerToken) {
        try {
            distributedLock.unlock(lockKey, ownerToken);
        } catch (RuntimeException ignored) {
            // The lock has a short TTL, so unlock failures must not change an already decided transfer result.
        }
    }

    private void validateParticipant(Transfer transfer, Long requestUserId) {
        // 송금 내역은 보낸 사람과 받은 사람만 조회할 수 있다.
        // 관리자 조회가 필요해지면 별도 권한과 API로 분리하는 편이 안전하다.
        if (!transfer.getSenderUserId().equals(requestUserId) && !transfer.getReceiverUserId().equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        return idempotencyKey.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null
                || amount.scale() > 0
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "amount must be an integer from 1 to 10000000");
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    private String createRequestHash(Long senderUserId, Long receiverUserId, BigDecimal amount) {
        // 요청 본문 전체를 그대로 저장하지 않고 SHA-256 해시만 저장한다.
        // 개인정보/민감정보 노출을 줄이면서도 "같은 요청인지" 비교할 수 있다.
        String raw = senderUserId + ":" + receiverUserId + ":" + amount.toPlainString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String resolveMessage(RuntimeException exception) {
        // 실패 사유는 운영자가 원인을 파악할 단서가 되지만, DB 컬럼 길이를 넘으면 저장에 실패한다.
        // 메시지가 없으면 예외 클래스명이라도 남기고, 너무 길면 500자로 자른다.
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
