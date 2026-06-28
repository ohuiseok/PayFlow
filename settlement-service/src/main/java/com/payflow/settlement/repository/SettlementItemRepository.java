package com.payflow.settlement.repository;

import com.payflow.settlement.entity.ReconciliationStatus;
import com.payflow.settlement.entity.SettlementItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {
    Optional<SettlementItem> findByEventId(String eventId);
    List<SettlementItem> findBySettlementRunId(Long settlementRunId);
    long countBySettlementRunIdAndStatusNot(Long settlementRunId, ReconciliationStatus status);
}
