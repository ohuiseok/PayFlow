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
        name = "open_banking_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_open_banking_tokens_user_type", columnNames = {"userId", "tokenType"})
        }
)
public class OpenBankingToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpenBankingTokenType tokenType;

    @Column(length = 30)
    private String userSeqNo;

    @Column(length = 20)
    private String clientUseCode;

    @Column(nullable = false, length = 2000)
    private String accessTokenEncrypted;

    @Column(length = 2000)
    private String refreshTokenEncrypted;

    @Column(length = 100)
    private String scope;

    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected OpenBankingToken() {
    }

    public OpenBankingToken(Long userId, OpenBankingTokenType tokenType) {
        this.userId = userId;
        this.tokenType = tokenType;
    }

    public void update(
            String userSeqNo,
            String clientUseCode,
            String accessTokenEncrypted,
            String refreshTokenEncrypted,
            String scope,
            LocalDateTime expiresAt
    ) {
        this.userSeqNo = userSeqNo;
        this.clientUseCode = clientUseCode;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.scope = scope;
        this.expiresAt = expiresAt;
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

    public Long getUserId() {
        return userId;
    }

    public OpenBankingTokenType getTokenType() {
        return tokenType;
    }

    public String getUserSeqNo() {
        return userSeqNo;
    }

    public String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    public String getRefreshTokenEncrypted() {
        return refreshTokenEncrypted;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
