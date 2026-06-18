package com.payflow.transfer.repository;

import com.payflow.transfer.entity.Transfer;
import com.payflow.transfer.entity.TransferStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    List<Transfer> findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(Long senderUserId, Long receiverUserId);

    List<Transfer> findByStatusOrderByCreatedAtDesc(TransferStatus status);

    Optional<Transfer> findByIdAndStatus(Long id, TransferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from Transfer transfer where transfer.id = :transferId")
    Optional<Transfer> findByIdForUpdate(@Param("transferId") Long transferId);
}
