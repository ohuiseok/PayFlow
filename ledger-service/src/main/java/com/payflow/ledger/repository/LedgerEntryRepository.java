package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    boolean existsByTransferId(Long transferId);

    Optional<LedgerEntry> findByTransferId(Long transferId);

    Optional<LedgerEntry> findBySourceTypeAndSourceId(LedgerSourceType sourceType, Long sourceId);

    List<LedgerEntry> findTop100ByOrderByCreatedAtDesc();
}
