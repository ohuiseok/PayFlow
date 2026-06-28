package com.payflow.settlement.batch;

import com.payflow.settlement.entity.SettlementItem;
import com.payflow.settlement.repository.SettlementItemRepository;
import com.payflow.settlement.repository.SettlementRunRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

@RequiredArgsConstructor
public class SettlementReconciliationWriter implements ItemWriter<SettlementReconciliationResult> {
    private final LocalDate businessDate;
    private final SettlementRunRepository runRepository;
    private final SettlementItemRepository itemRepository;

    @Override
    public void write(Chunk<? extends SettlementReconciliationResult> chunk) {
        Long runId = runRepository.findByBusinessDate(businessDate).orElseThrow().getId();
        for (SettlementReconciliationResult result : chunk) {
            SettlementItem item = itemRepository.findByEventId(result.transaction().getEventId())
                    .orElseGet(() -> new SettlementItem(runId, result.transaction()));
            if (result.ledgerEntry() == null) item.apply(null, null);
            else item.apply(result.ledgerEntry().id(), result.ledgerEntry().amount());
            itemRepository.save(item);
        }
    }
}
