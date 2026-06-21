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
import java.time.LocalDateTime;

@Entity
@Table(name = "open_banking_authorizations")
public class OpenBankingAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 255)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OpenBankingAuthorizationStatus status;

    @Column(length = 50)
    private String userSeqNo;

    @Column(length = 2000)
    private String accessTokenEncrypted;

    @Column(length = 2000)
    private String refreshTokenEncrypted;

    private LocalDateTime tokenExpiresAt;

    @Column(length = 30)
    private String userRole;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected OpenBankingAuthorization() {
    }

    public OpenBankingAuthorization(Long userId, String state) {
        this.userId = userId;
        this.state = state;
        this.status = OpenBankingAuthorizationStatus.REQUESTED;
    }

    public OpenBankingAuthorization(Long userId, String state, String userRole) {
        this.userId = userId;
        this.state = state;
        this.userRole = userRole;
        this.status = OpenBankingAuthorizationStatus.REQUESTED;
    }

    public void connect(String userSeqNo, String accessTokenEncrypted, String refreshTokenEncrypted, LocalDateTime tokenExpiresAt) {
        this.userSeqNo = userSeqNo;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.tokenExpiresAt = tokenExpiresAt;
        this.status = OpenBankingAuthorizationStatus.CONNECTED;
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

    public String getState() {
        return state;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserRole() {
        return userRole;
    }
}
