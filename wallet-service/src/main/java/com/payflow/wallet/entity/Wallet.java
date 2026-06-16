package com.payflow.wallet.entity;

import com.payflow.wallet.support.error.BusinessException;
import com.payflow.wallet.support.error.ErrorCode;
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
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WalletStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Wallet() {
        // JPA는 엔티티를 DB에서 읽어올 때 기본 생성자를 사용한다.
        // 외부 코드가 의미 없는 Wallet을 만들지 못하도록 protected로 둔다.
    }

    public Wallet(Long userId) {
        // 지갑은 생성 직후 잔액 0, 사용 가능 상태로 시작한다.
        // 금액은 BigDecimal을 사용해 부동소수점 오차 없이 정수 원 단위로 저장한다.
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
    }

    public BigDecimal deposit(BigDecimal amount) {
        // 입금/출금 전에 상태를 확인하면 잠긴 지갑에 돈이 움직이는 사고를 막을 수 있다.
        validateActive();
        this.balance = this.balance.add(amount);
        return this.balance;
    }

    public BigDecimal withdraw(BigDecimal amount) {
        validateActive();
        // 잔액 부족 검사는 지갑 도메인의 핵심 불변식이다.
        // 이 검사를 서비스 밖으로 흩어 놓으면 어떤 경로에서는 음수 잔액이 생길 수 있다.
        if (this.balance.compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
        return this.balance;
    }

    public void lock() {
        this.status = WalletStatus.LOCKED;
    }

    private void validateActive() {
        if (this.status != WalletStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.WALLET_LOCKED);
        }
    }

    @PrePersist
    void prePersist() {
        // @PrePersist는 INSERT 직전에 실행된다.
        // 생성/수정 시각을 애플리케이션에서 일관되게 채우기 위한 JPA 생명주기 콜백이다.
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

    public BigDecimal getBalance() {
        return balance;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
