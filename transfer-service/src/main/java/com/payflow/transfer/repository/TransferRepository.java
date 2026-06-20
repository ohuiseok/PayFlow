package com.payflow.transfer.repository;

import com.payflow.transfer.entity.Transfer;
import com.payflow.transfer.entity.TransferStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    // [M-3] Pageable을 사용해 대량 데이터를 페이지 단위로 조회한다.
    // sender_user_id, receiver_user_id 인덱스는 V1 Flyway 마이그레이션에서 생성했다.
    Page<Transfer> findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(Long senderUserId, Long receiverUserId, Pageable pageable);

    // 기존 List 반환 메서드는 내부 보상 처리에서만 사용하므로 그대로 유지한다.
    List<Transfer> findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(Long senderUserId, Long receiverUserId);

    List<Transfer> findByStatusOrderByCreatedAtDesc(TransferStatus status);

    Optional<Transfer> findByIdAndStatus(Long id, TransferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from Transfer transfer where transfer.id = :transferId")
    Optional<Transfer> findByIdForUpdate(@Param("transferId") Long transferId);

    // [M-6] 지정 시각 이전에 생성되었는데 아직 PROCESSING 상태인 고착 송금을 조회한다.
    List<Transfer> findByStatusAndCreatedAtBefore(TransferStatus status, LocalDateTime createdBefore);
}
