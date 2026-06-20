package com.payflow.ledger.repository;

import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    boolean existsByTransferId(Long transferId);

    Optional<LedgerEntry> findByTransferId(Long transferId);

    Optional<LedgerEntry> findBySourceTypeAndSourceId(LedgerSourceType sourceType, Long sourceId);

    List<LedgerEntry> findTop100ByOrderByCreatedAtDesc();

    // [C-4] 본인이 관여한 원장 엔트리만 반환한다.
    // senderUserId 또는 receiverUserId가 요청자와 일치하는 항목만 조회해 타인 원장 노출을 방지한다.
    @Query("SELECT e FROM LedgerEntry e WHERE e.senderUserId = :userId OR e.receiverUserId = :userId ORDER BY e.createdAt DESC")
    List<LedgerEntry> findTop100ByUserId(@Param("userId") Long userId);
}
