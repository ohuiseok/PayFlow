package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerLine;
import com.payflow.ledger.entity.LedgerSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerLineRepository extends JpaRepository<LedgerLine, Long> {

    long countByLedgerEntryTransferId(Long transferId);

    long countByLedgerEntry_SourceTypeAndLedgerEntry_SourceId(
            LedgerSourceType sourceType,
            Long sourceId
    );
}
