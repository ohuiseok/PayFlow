package com.payflow.settlement.batch;

import com.payflow.settlement.client.LedgerEntrySnapshot;
import com.payflow.settlement.entity.SettlementTransaction;

public record SettlementReconciliationResult(
        SettlementTransaction transaction,
        LedgerEntrySnapshot ledgerEntry
) {
}
