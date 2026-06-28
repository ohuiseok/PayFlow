package com.payflow.settlement.client;

import java.math.BigDecimal;

public record LedgerEntrySnapshot(Long id, BigDecimal amount) {
}
