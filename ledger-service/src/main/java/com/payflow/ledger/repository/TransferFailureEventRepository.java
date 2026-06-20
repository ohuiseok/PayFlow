package com.payflow.ledger.repository;

import com.payflow.ledger.entity.TransferFailureEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferFailureEventRepository extends JpaRepository<TransferFailureEvent, Long> {

    Optional<TransferFailureEvent> findByTransferId(Long transferId);

    List<TransferFailureEvent> findAllByOrderByCreatedAtDesc();

    // [C-4] 본인이 관여한 송금 실패 이벤트만 반환한다.
    @Query("SELECT f FROM TransferFailureEvent f WHERE f.senderUserId = :userId OR f.receiverUserId = :userId ORDER BY f.createdAt DESC")
    List<TransferFailureEvent> findByUserId(@Param("userId") Long userId);
}
