package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerLineRepository extends JpaRepository<LedgerLine, Long> {

    long countByLedgerEntryTransferId(Long transferId);
}
