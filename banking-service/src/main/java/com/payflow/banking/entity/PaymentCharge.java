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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_charges")
public class PaymentCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentChargeMethod chargeMethod;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentChargeStatus status;

    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    private Long walletId;

    @Column(unique = true)
    private Long walletTransactionId;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private int compensationRetryCount;

    @Column(length = 500)
    private String compensationFailureReason;

    private LocalDateTime compensatedAt;

    @Column(nullable = false)
    private boolean ledgerRecorded;

    @Column(length = 40)
    private String ledgerRecordType;

    @Column(length = 500)
    private String ledgerFailureReason;

    @Column(nullable = false)
    private int ledgerRetryCount;

    private LocalDateTime ledgerRecordedAt;

    private LocalDateTime completedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PaymentCharge() {
    }

    public PaymentCharge(Long userId, BigDecimal amount, String idempotencyKey, String requestHash) {
        this.userId = userId;
        this.providerCode = "TOSS_PAYMENTS";
        this.chargeMethod = PaymentChargeMethod.TOSS_WIDGET;
        this.amount = amount;
        this.currency = "KRW";
        this.status = PaymentChargeStatus.READY;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void markPaymentApproved() {
        this.status = PaymentChargeStatus.PAYMENT_APPROVED;
        this.failureCode = null;
        this.failureReason = null;
    }

    public void markWalletReflecting() {
        this.status = PaymentChargeStatus.WALLET_REFLECTING;
    }

    public void complete(Long walletId, Long walletTransactionId) {
        this.walletId = walletId;
        this.walletTransactionId = walletTransactionId;
        this.status = PaymentChargeStatus.COMPLETED;
        this.failureCode = null;
        this.failureReason = null;
        this.compensationFailureReason = null;
        this.compensatedAt = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
    }

    public void markLedgerRecorded(String ledgerRecordType) {
        this.ledgerRecorded = true;
        this.ledgerRecordType = ledgerRecordType;
        this.ledgerFailureReason = null;
        this.ledgerRecordedAt = LocalDateTime.now();
    }

    public void requireLedgerCompensation(String ledgerRecordType, String reason) {
        this.ledgerRecorded = false;
        this.ledgerRecordType = ledgerRecordType;
        this.ledgerFailureReason = reason;
    }

    public void recordLedgerCompensationFailure(String reason) {
        this.ledgerRetryCount += 1;
        this.ledgerFailureReason = reason;
    }

    public void fail(String code, String reason) {
        this.status = PaymentChargeStatus.FAILED;
        this.failureCode = code;
        this.failureReason = reason;
    }

    public void requireCompensation(String code, String reason) {
        this.status = PaymentChargeStatus.COMPENSATION_REQUIRED;
        this.failureCode = code;
        this.failureReason = reason;
    }

    public void recordCompensationFailure(String reason) {
        this.compensationRetryCount += 1;
        this.compensationFailureReason = reason;
    }

    public void cancel(boolean partial) {
        this.status = partial ? PaymentChargeStatus.PARTIAL_CANCELED : PaymentChargeStatus.CANCELED;
        this.failureCode = null;
        this.failureReason = null;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentChargeStatus getStatus() {
        return status;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getWalletId() {
        return walletId;
    }

    public Long getWalletTransactionId() {
        return walletTransactionId;
    }

    public String getFailureCode() {
        return failureCode;
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

    public boolean isLedgerRecorded() {
        return ledgerRecorded;
    }

    public String getLedgerRecordType() {
        return ledgerRecordType;
    }

    public String getLedgerFailureReason() {
        return ledgerFailureReason;
    }

    public int getLedgerRetryCount() {
        return ledgerRetryCount;
    }

    public LocalDateTime getLedgerRecordedAt() {
        return ledgerRecordedAt;
    }
}
