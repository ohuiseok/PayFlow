package com.payflow.ledger.dto;

import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerEntryType;
import com.payflow.ledger.entity.LedgerSourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LedgerEntryResponse(
        Long id,
        Long transferId,
        LedgerSourceType sourceType,
        Long sourceId,
        LedgerEntryType entryType,
        Long senderUserId,
        Long receiverUserId,
        BigDecimal amount,
        LocalDateTime createdAt,
        List<LedgerLineResponse> lines
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransferId(),
                entry.getSourceType(),
                entry.getSourceId(),
                entry.getEntryType(),
                entry.getSenderUserId(),
                entry.getReceiverUserId(),
                entry.getAmount(),
                entry.getCreatedAt(),
                entry.getLines().stream().map(LedgerLineResponse::from).toList()
        );
    }
}
