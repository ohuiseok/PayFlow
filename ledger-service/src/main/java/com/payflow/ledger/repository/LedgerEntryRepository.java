package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    boolean existsByTransferId(Long transferId);

    Optional<LedgerEntry> findByTransferId(Long transferId);
}
