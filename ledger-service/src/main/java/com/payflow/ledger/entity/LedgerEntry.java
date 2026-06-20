package com.payflow.ledger.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "ledger_entries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ledger_entries_source", columnNames = {"sourceType", "sourceId"})
        }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LedgerSourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LedgerEntryType entryType;

    private Long senderUserId;

    private Long receiverUserId;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "ledgerEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<LedgerLine> lines = new ArrayList<>();

    protected LedgerEntry() {
        // JPA 전용 생성자다. 원장 엔트리는 반드시 송금 정보와 함께 생성되어야 한다.
    }

    public LedgerEntry(Long transferId, Long senderUserId, Long receiverUserId, BigDecimal amount) {
        // LedgerEntry는 "한 업무 사건"의 원장 헤더다.
        // 여기서는 송금 1건이 원장 엔트리 1건이 되고, transferId unique 제약으로 중복 기록을 막는다.
        this.transferId = transferId;
        this.sourceType = LedgerSourceType.TRANSFER;
        this.sourceId = transferId;
        this.entryType = LedgerEntryType.TRANSFER;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.amount = amount;
        // 복식부기는 항상 차변과 대변의 합이 맞아야 한다.
        // 보낸 사람에게 DEBIT, 받은 사람에게 CREDIT 라인을 같은 금액으로 생성해 한 사건의 양쪽 흐름을 함께 남긴다.
        addLine(new LedgerLine(this, senderUserId, LedgerLineType.DEBIT, amount));
        addLine(new LedgerLine(this, receiverUserId, LedgerLineType.CREDIT, amount));
    }

    public static LedgerEntry paymentCharge(Long chargeId, Long userId, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.sourceType = LedgerSourceType.TOSS_CHARGE;
        entry.sourceId = chargeId;
        entry.entryType = LedgerEntryType.USER_WALLET_TOPUP;
        entry.receiverUserId = userId;
        entry.amount = amount;
        entry.addLine(new LedgerLine(entry, null, LedgerLineType.DEBIT, amount, "PG_CASH"));
        entry.addLine(new LedgerLine(entry, userId, LedgerLineType.CREDIT, amount, "USER_WALLET"));
        return entry;
    }

    public static LedgerEntry paymentCancel(Long chargeId, Long userId, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.sourceType = LedgerSourceType.TOSS_CANCEL;
        entry.sourceId = chargeId;
        entry.entryType = LedgerEntryType.PG_CANCEL;
        entry.senderUserId = userId;
        entry.amount = amount;
        entry.addLine(new LedgerLine(entry, userId, LedgerLineType.DEBIT, amount, "USER_WALLET"));
        entry.addLine(new LedgerLine(entry, null, LedgerLineType.CREDIT, amount, "PG_CASH"));
        return entry;
    }

    private void addLine(LedgerLine line) {
        this.lines.add(line);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public LedgerSourceType getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<LedgerLine> getLines() {
        return lines;
    }
}
