package com.payflow.transfer.repository;

import com.payflow.transfer.entity.Transfer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    List<Transfer> findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(Long senderUserId, Long receiverUserId);
}
