package com.payflow.ledger.repository;

import com.payflow.ledger.entity.TransferFailureEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferFailureEventRepository extends JpaRepository<TransferFailureEvent, Long> {

    Optional<TransferFailureEvent> findByTransferId(Long transferId);

    List<TransferFailureEvent> findAllByOrderByCreatedAtDesc();
}
