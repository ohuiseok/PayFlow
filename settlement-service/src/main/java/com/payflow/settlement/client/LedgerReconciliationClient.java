package com.payflow.settlement.client;

import java.util.Optional;

public interface LedgerReconciliationClient {
    Optional<LedgerEntrySnapshot> findPaymentEntry(String sourceType, Long sourceId);
}
