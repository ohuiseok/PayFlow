package com.payflow.settlement.batch;

import com.payflow.settlement.client.LedgerReconciliationClient;
import com.payflow.settlement.entity.SettlementTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementReconciliationProcessor implements ItemProcessor<SettlementTransaction, SettlementReconciliationResult> {
    private final LedgerReconciliationClient ledgerClient;

    @Override
    public SettlementReconciliationResult process(SettlementTransaction item) {
        return new SettlementReconciliationResult(
                item,
                ledgerClient.findPaymentEntry(item.getLedgerSourceType(), item.getChargeId()).orElse(null)
        );
    }
}
