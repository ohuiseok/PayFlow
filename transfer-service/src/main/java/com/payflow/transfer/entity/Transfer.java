package com.payflow.transfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transfers",
        indexes = {
                @Index(name = "idx_transfers_sender_user_id", columnList = "sender_user_id"),
                @Index(name = "idx_transfers_receiver_user_id", columnList = "receiver_user_id"),
                @Index(name = "idx_transfers_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transfers_idempotency_key", columnNames = "idempotencyKey")
        }
)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false)
    private Long receiverUserId;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferStatus status;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    // 같은 멱등키가 정말 같은 요청에 쓰였는지 확인하기 위한 SHA-256 해시다.
    // 원문 요청을 저장하지 않아도 충돌 여부를 판단할 수 있다.
    @Column(nullable = false, length = 64)
    private String requestHash;

    private Long senderWalletId;

    private Long receiverWalletId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private int compensationRetryCount;

    @Column(length = 500)
    private String compensationFailureReason;

    private LocalDateTime compensatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Transfer() {
        // JPA가 엔티티를 복원할 때 사용하는 생성자다.
    }

    public Transfer(Long senderUserId, Long receiverUserId, BigDecimal amount, String idempotencyKey, String requestHash) {
        // 송금은 처음 생성될 때 아직 지갑 반영이 시작되지 않았으므로 REQUESTED 상태로 둔다.
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = TransferStatus.REQUESTED;
    }

    public void start(Long senderWalletId, Long receiverWalletId) {
        // 지갑 ID를 저장해 두면 장애가 난 뒤에도 어떤 지갑까지 처리했는지 추적할 수 있다.
        this.senderWalletId = senderWalletId;
        this.receiverWalletId = receiverWalletId;
        this.status = TransferStatus.PROCESSING;
    }

    public void succeed() {
        // 성공 상태에서는 실패 사유가 남아 있으면 혼란스럽기 때문에 명시적으로 비운다.
        this.status = TransferStatus.SUCCEEDED;
        this.failureReason = null;
    }

    public void fail(String failureReason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void requireCompensation(String failureReason) {
        // 보상 필요 상태는 단순 실패보다 더 위험한 중간 상태다.
        // 예를 들어 출금은 됐는데 입금이 안 된 경우 재시도나 환불 처리가 필요하다.
        this.status = TransferStatus.COMPENSATION_REQUIRED;
        this.failureReason = failureReason;
    }

    public void compensate() {
        this.status = TransferStatus.COMPENSATED;
        this.failureReason = null;
        this.compensationFailureReason = null;
        this.compensatedAt = LocalDateTime.now();
    }

    public void recordCompensationFailure(String failureReason) {
        this.compensationRetryCount++;
        this.compensationFailureReason = normalizeMessage(failureReason);
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getSenderWalletId() {
        return senderWalletId;
    }

    public Long getReceiverWalletId() {
        return receiverWalletId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getCompensationRetryCount() {
        return compensationRetryCount;
    }

    public String getCompensationFailureReason() {
        return compensationFailureReason;
    }

    public LocalDateTime getCompensatedAt() {
        return compensatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
