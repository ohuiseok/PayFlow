package com.payflow.ledger.dto;

import com.payflow.ledger.entity.LedgerLine;
import com.payflow.ledger.entity.LedgerLineType;
import java.math.BigDecimal;

public record LedgerLineResponse(
        Long id,
        Long userId,
        String accountCode,
        LedgerLineType type,
        BigDecimal amount
) {
    public static LedgerLineResponse from(LedgerLine line) {
        return new LedgerLineResponse(
                line.getId(),
                line.getUserId(),
                line.getAccountCode(),
                line.getType(),
                line.getAmount()
        );
    }
}
