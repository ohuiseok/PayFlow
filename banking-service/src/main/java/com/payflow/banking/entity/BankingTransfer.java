package com.payflow.banking.entity;

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
        name = "banking_transfers",
        indexes = {
                @Index(name = "idx_banking_transfers_user", columnList = "user_id"),
                @Index(name = "idx_banking_transfers_status_next_check", columnList = "status, next_result_check_at")
        },
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankingTransferType transferType;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BankingTransferStatus status;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, unique = true, length = 80)
    private String bankTranId;

    @Column(length = 8)
    private String bankTranDate;

    @Column(length = 14)
    private String tranDtime;

    @Column(length = 80)
    private String apiTranId;

    @Column(length = 20)
    private String apiResponseCode;

    @Column(length = 20)
    private String bankResponseCode;

    @Column(length = 50)
    private String walletReferenceType;

    @Column(length = 100)
    private String walletReferenceId;

    @Column(nullable = false)
    private int resultCheckCount;

    private LocalDateTime nextResultCheckAt;

    private LocalDateTime lastResultCheckedAt;

    private LocalDateTime completedAt;

    private Long walletTransactionId;

    @Column(nullable = false)
    private int compensationRetryCount;

    @Column(length = 500)
    private String compensationFailureReason;

    private LocalDateTime compensatedAt;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BankingTransfer() {
    }

    public BankingTransfer(
            Long userId,
            Long bankAccountId,
            BigDecimal amount,
            String idempotencyKey,
            String requestHash,
            String bankTranId,
            BankingTransferType transferType
    ) {
        this.userId = userId;
        this.bankAccountId = bankAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.bankTranId = bankTranId;
        this.transferType = transferType;
        this.status = BankingTransferStatus.REQUESTED;
    }

    public void markWalletWithdrawing(String walletReferenceType, String walletReferenceId) {
        this.walletReferenceType = walletReferenceType;
        this.walletReferenceId = walletReferenceId;
        this.status = BankingTransferStatus.WALLET_WITHDRAWING;
    }

    public void markBankSucceeded(String bankTranDate, String apiTranId, String apiResponseCode, String bankResponseCode) {
        this.bankTranDate = bankTranDate;
        this.apiTranId = apiTranId;
        this.apiResponseCode = apiResponseCode;
        this.bankResponseCode = bankResponseCode;
        this.status = BankingTransferStatus.BANK_SUCCEEDED;
        this.failureReason = null;
    }

    public void markBankProcessing(String apiResponseCode, String bankResponseCode, String failureReason) {
        this.apiResponseCode = apiResponseCode;
        this.bankResponseCode = bankResponseCode;
        this.status = BankingTransferStatus.BANK_PROCESSING;
        this.failureReason = failureReason;
        this.nextResultCheckAt = LocalDateTime.now().plusMinutes(1);
    }

    public void markBankProcessing(String bankTranDate, String apiResponseCode, String bankResponseCode, String failureReason) {
        this.bankTranDate = bankTranDate;
        markBankProcessing(apiResponseCode, bankResponseCode, failureReason);
    }

    public void markBankFailed(String apiResponseCode, String bankResponseCode, String failureReason) {
        this.apiResponseCode = apiResponseCode;
        this.bankResponseCode = bankResponseCode;
        this.status = BankingTransferStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void markCompensationRequired(String failureReason) {
        this.status = BankingTransferStatus.COMPENSATION_REQUIRED;
        this.failureReason = failureReason;
    }

    public void markCompensated(Long walletId, String walletReferenceType, String walletReferenceId) {
        this.walletId = walletId;
        this.walletReferenceType = walletReferenceType;
        this.walletReferenceId = walletReferenceId;
        this.status = BankingTransferStatus.COMPENSATED;
        this.failureReason = null;
        this.compensationFailureReason = null;
        this.compensatedAt = LocalDateTime.now();
    }

    public void recordCompensationFailure(String reason) {
        this.compensationRetryCount += 1;
        this.compensationFailureReason = reason;
    }

    public void markWalletReflecting(String walletReferenceType, String walletReferenceId) {
        this.walletReferenceType = walletReferenceType;
        this.walletReferenceId = walletReferenceId;
        this.status = BankingTransferStatus.WALLET_REFLECTING;
    }

    public void succeed(Long walletId, Long walletTransactionId) {
        this.walletId = walletId;
        this.walletTransactionId = walletTransactionId;
        this.status = BankingTransferStatus.COMPLETED;
        this.failureReason = null;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String failureReason) {
        this.status = BankingTransferStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void recordResultCheck() {
        this.resultCheckCount += 1;
        this.lastResultCheckedAt = LocalDateTime.now();
        this.nextResultCheckAt = LocalDateTime.now().plusMinutes(Math.min(16, 1L << Math.min(resultCheckCount, 4)));
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

    public BankingTransferType getTransferType() {
        return transferType;
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

    public String getBankTranDate() {
        return bankTranDate;
    }

    public String getTranDtime() {
        return tranDtime;
    }

    public String getApiTranId() {
        return apiTranId;
    }

    public String getApiResponseCode() {
        return apiResponseCode;
    }

    public String getBankResponseCode() {
        return bankResponseCode;
    }

    public Long getWalletTransactionId() {
        return walletTransactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getResultCheckCount() {
        return resultCheckCount;
    }

    public LocalDateTime getNextResultCheckAt() {
        return nextResultCheckAt;
    }

    public LocalDateTime getLastResultCheckedAt() {
        return lastResultCheckedAt;
    }

    public String getWalletReferenceType() {
        return walletReferenceType;
    }

    public String getWalletReferenceId() {
        return walletReferenceId;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
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
}
