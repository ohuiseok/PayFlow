package com.payflow.settlement.repository;

import com.payflow.settlement.entity.SettlementTransaction;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, Long> {
    Optional<SettlementTransaction> findByEventId(String eventId);
    Page<SettlementTransaction> findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            LocalDateTime start, LocalDateTime end, Pageable pageable);
}
