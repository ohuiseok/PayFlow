package com.payflow.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ledger_lines")
public class LedgerLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_entry_id", nullable = false)
    private LedgerEntry ledgerEntry;

    @Column
    private Long userId;

    @Column(nullable = false, length = 50)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerLineType type;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    protected LedgerLine() {
        // JPA 전용 생성자다. 라인은 항상 어떤 LedgerEntry에 속해야 하므로 public 기본 생성자를 열지 않는다.
    }

    public LedgerLine(LedgerEntry ledgerEntry, Long userId, LedgerLineType type, BigDecimal amount) {
        this(ledgerEntry, userId, type, amount, type == LedgerLineType.DEBIT ? "USER_WALLET_OUT" : "USER_WALLET_IN");
    }

    public LedgerLine(LedgerEntry ledgerEntry, Long userId, LedgerLineType type, BigDecimal amount, String accountCode) {
        // 한 줄은 특정 사용자에게 발생한 회계 방향과 금액을 나타낸다.
        // 여러 라인이 하나의 LedgerEntry에 묶여야 "한 송금 사건" 전체를 추적할 수 있다.
        this.ledgerEntry = ledgerEntry;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.accountCode = accountCode;
    }

    public Long getId() {
        return id;
    }

    public LedgerEntry getLedgerEntry() {
        return ledgerEntry;
    }

    public Long getUserId() {
        return userId;
    }

    public LedgerLineType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAccountCode() {
        return accountCode;
    }
}
