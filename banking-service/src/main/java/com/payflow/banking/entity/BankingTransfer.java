package com.payflow.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "banking_transfers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_banking_transfers_idempotency_key", columnNames = "idempotencyKey")
        }
)
public class BankingTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long bankAccountId;

    private Long walletId;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankingTransferStatus status;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, unique = true, length = 80)
    private String bankTranId;

    private Long walletTransactionId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BankingTransfer() {
    }

    public BankingTransfer(Long userId, Long bankAccountId, BigDecimal amount, String idempotencyKey, String requestHash, String bankTranId) {
        this.userId = userId;
        this.bankAccountId = bankAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.bankTranId = bankTranId;
        this.status = BankingTransferStatus.REQUESTED;
    }

    public void succeed(Long walletId, Long walletTransactionId) {
        this.walletId = walletId;
        this.walletTransactionId = walletTransactionId;
        this.status = BankingTransferStatus.SUCCEEDED;
        this.failureReason = null;
    }

    public void fail(String failureReason) {
        this.status = BankingTransferStatus.FAILED;
        this.failureReason = failureReason;
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

    public Long getUserId() {
        return userId;
    }

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BankingTransferStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getBankTranId() {
        return bankTranId;
    }

    public Long getWalletTransactionId() {
        return walletTransactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
