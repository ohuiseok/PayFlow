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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TransferService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final String TRANSFER_REFERENCE_TYPE = "TRANSFER";
    private static final String TRANSFER_COMPENSATION_REFERENCE_TYPE = "TRANSFER_COMPENSATION";

    private final TransferRepository transferRepository;
    private final WalletClient walletClient;
    private final TransferEventPublisher transferEventPublisher;
    private final DistributedLock distributedLock;
    private final PlatformTransactionManager transactionManager;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Value("${transfer.wallet-lock.ttl:5s}")
    private Duration walletLockTtl;

    public TransferResponse createTransfer(CreateTransferRequest request, String idempotencyKey, Long senderUserId) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        BigDecimal amount = normalizeAmount(request.amount());
        if (senderUserId.equals(request.receiverUserId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "sender and receiver must be different");
        }

        String requestHash = createRequestHash(senderUserId, request.receiverUserId(), amount);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        InitialTransfer initialTransfer = transactionTemplate.execute(status ->
                findOrCreateInitialTransfer(senderUserId, request.receiverUserId(), amount, normalizedIdempotencyKey, requestHash)
        );
        if (initialTransfer == null) {
            throw new IllegalStateException("Failed to initialize transfer");
        }
        if (!initialTransfer.created()) {
            return resolveExistingTransfer(initialTransfer.transfer(), requestHash);
        }
        return processCreatedTransfer(initialTransfer.transfer(), transactionTemplate);
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

    @Transactional(noRollbackFor = BusinessException.class)
    public TransferResponse refundCompensation(Long transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
        if (transfer.getStatus() == TransferStatus.COMPENSATED) {
            return TransferResponse.from(transfer);
        }
        if (transfer.getStatus() != TransferStatus.COMPENSATION_REQUIRED || transfer.getSenderWalletId() == null) {
            throw new BusinessException(ErrorCode.INVALID_TRANSFER_STATUS);
        }

        try {
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
        } catch (RuntimeException exception) {
            String failureMessage = resolveMessage(exception);
            transfer.recordCompensationFailure(failureMessage);
            throw new BusinessException(ErrorCode.COMPENSATION_REFUND_FAILED, failureMessage);
        }
    }

    private InitialTransfer findOrCreateInitialTransfer(
            Long senderUserId,
            Long receiverUserId,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash
    ) {
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> new InitialTransfer(existing, false))
                .orElseGet(() -> createInitialTransfer(senderUserId, receiverUserId, amount, idempotencyKey, requestHash));
    }

    private InitialTransfer createInitialTransfer(
            Long senderUserId,
            Long receiverUserId,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash
    ) {
        try {
            Transfer transfer = transferRepository.saveAndFlush(new Transfer(senderUserId, receiverUserId, amount, idempotencyKey, requestHash));
            return new InitialTransfer(transfer, true);
        } catch (DataIntegrityViolationException exception) {
            return transferRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> new InitialTransfer(existing, false))
                    .orElseThrow(() -> exception);
        }
    }

    private TransferResponse resolveExistingTransfer(Transfer transfer, String requestHash) {
        if (!transfer.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        return TransferResponse.from(transfer);
    }

    private TransferResponse processCreatedTransfer(Transfer transfer, TransactionTemplate transactionTemplate) {
        Long transferId = transfer.getId();
        try {
            var senderWallet = walletClient.getWalletByUserId(transfer.getSenderUserId(), true, internalSecret);
            var receiverWallet = walletClient.getWalletByUserId(transfer.getReceiverUserId(), true, internalSecret);
            String lockKey = "transfer:wallet-lock:" + senderWallet.walletId();
            String ownerToken = UUID.randomUUID().toString();
            if (!distributedLock.tryLock(lockKey, ownerToken, walletLockTtl)) {
                throw new BusinessException(ErrorCode.WALLET_LOCK_CONFLICT);
            }

            try {
                markTransferStarted(transactionTemplate, transferId, senderWallet.walletId(), receiverWallet.walletId());
                String referenceId = transferId.toString();
                walletClient.withdraw(
                        senderWallet.walletId(),
                        new WalletBalanceChangeRequest(transfer.getAmount(), TRANSFER_REFERENCE_TYPE, referenceId),
                        true,
                        internalSecret
                );
                try {
                    walletClient.deposit(
                            receiverWallet.walletId(),
                            new WalletBalanceChangeRequest(transfer.getAmount(), TRANSFER_REFERENCE_TYPE, referenceId),
                            true,
                            internalSecret
                    );
                } catch (RuntimeException exception) {
                    return markTransferCompensationRequired(transactionTemplate, transferId, resolveMessage(exception));
                }
                return markTransferSucceeded(transactionTemplate, transferId);
            } finally {
                releaseLockQuietly(lockKey, ownerToken);
            }
        } catch (RuntimeException exception) {
            return markTransferFailed(transactionTemplate, transferId, resolveMessage(exception));
        }
    }

    private Transfer markTransferStarted(TransactionTemplate transactionTemplate, Long transferId, Long senderWalletId, Long receiverWalletId) {
        return transactionTemplate.execute(status -> {
            Transfer transfer = findTransferForUpdate(transferId);
            transfer.start(senderWalletId, receiverWalletId);
            return transfer;
        });
    }

    private TransferResponse markTransferSucceeded(TransactionTemplate transactionTemplate, Long transferId) {
        return transactionTemplate.execute(status -> {
            Transfer transfer = findTransferForUpdate(transferId);
            transfer.succeed();
            transferEventPublisher.publishCompleted(transfer);
            return TransferResponse.from(transfer);
        });
    }

    private TransferResponse markTransferFailed(TransactionTemplate transactionTemplate, Long transferId, String failureMessage) {
        return transactionTemplate.execute(status -> {
            Transfer transfer = findTransferForUpdate(transferId);
            transfer.fail(failureMessage);
            transferEventPublisher.publishFailed(transfer);
            return TransferResponse.from(transfer);
        });
    }

    private TransferResponse markTransferCompensationRequired(TransactionTemplate transactionTemplate, Long transferId, String failureMessage) {
        return transactionTemplate.execute(status -> {
            Transfer transfer = findTransferForUpdate(transferId);
            transfer.requireCompensation(failureMessage);
            transferEventPublisher.publishFailed(transfer);
            return TransferResponse.from(transfer);
        });
    }

    private Transfer findTransferForUpdate(Long transferId) {
        return transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
    }

    private void releaseLockQuietly(String lockKey, String ownerToken) {
        try {
            distributedLock.unlock(lockKey, ownerToken);
        } catch (RuntimeException ignored) {
            // The lock has a short TTL, so unlock failures must not change an already decided transfer result.
        }
    }

    private void validateParticipant(Transfer transfer, Long requestUserId) {
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
        String raw = senderUserId + ":" + receiverUserId + ":" + amount.toPlainString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String resolveMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record InitialTransfer(Transfer transfer, boolean created) {
    }
}
