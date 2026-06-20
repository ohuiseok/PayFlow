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
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bank_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bank_accounts_user_bank_account", columnNames = {"userId", "bankCode", "accountNumber"})
        }
)
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String bankCode;

    @Column(nullable = false, length = 80)
    private String accountNumber;

    @Column(nullable = false, length = 80)
    private String accountNumberMasked;

    @Column(nullable = false, length = 100)
    private String accountHolderName;

    @Column(length = 30)
    private String fintechUseNum;

    @Column(length = 20)
    private String userSeqNo;

    @Column(length = 100)
    private String bankName;

    @Column(length = 5)
    private String inquiryAgreeYn;

    @Column(length = 5)
    private String transferAgreeYn;

    @Column(length = 30)
    private String providerCode;

    private Long openBankingAuthorizationId;

    @Column(length = 2000)
    private String fintechUseNumEncrypted;

    @Column(length = 100)
    private String accountAlias;

    private LocalDateTime linkedAt;

    private LocalDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankAccountStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BankAccount() {
    }

    public BankAccount(
            Long userId,
            String bankCode,
            String accountNumber,
            String accountNumberMasked,
            String accountHolderName,
            String fintechUseNum,
            String userSeqNo,
            String bankName,
            String inquiryAgreeYn,
            String transferAgreeYn
    ) {
        this.userId = userId;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountNumberMasked = accountNumberMasked;
        this.accountHolderName = accountHolderName;
        this.fintechUseNum = fintechUseNum;
        this.userSeqNo = userSeqNo;
        this.bankName = bankName;
        this.inquiryAgreeYn = inquiryAgreeYn;
        this.transferAgreeYn = transferAgreeYn;
        this.providerCode = fintechUseNum == null ? "MANUAL" : "OPEN_BANKING";
        this.linkedAt = fintechUseNum == null ? null : LocalDateTime.now();
        this.lastSyncedAt = fintechUseNum == null ? null : LocalDateTime.now();
        this.status = BankAccountStatus.ACTIVE;
    }

    public void markOpenBankingAuthorization(Long openBankingAuthorizationId, String fintechUseNumEncrypted, String accountAlias) {
        this.openBankingAuthorizationId = openBankingAuthorizationId;
        this.fintechUseNumEncrypted = fintechUseNumEncrypted;
        this.accountAlias = accountAlias;
        this.providerCode = "OPEN_BANKING";
        this.linkedAt = this.linkedAt == null ? LocalDateTime.now() : this.linkedAt;
        this.lastSyncedAt = LocalDateTime.now();
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

    public String getBankCode() {
        return bankCode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountNumberMasked() {
        return accountNumberMasked;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public BankAccountStatus getStatus() {
        return status;
    }

    public String getFintechUseNum() {
        return fintechUseNum;
    }

    public String getUserSeqNo() {
        return userSeqNo;
    }

    public String getBankName() {
        return bankName;
    }

    public String getInquiryAgreeYn() {
        return inquiryAgreeYn;
    }

    public String getTransferAgreeYn() {
        return transferAgreeYn;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Long getOpenBankingAuthorizationId() {
        return openBankingAuthorizationId;
    }

    public String getAccountAlias() {
        return accountAlias;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }
}
