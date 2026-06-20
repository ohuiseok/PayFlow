package com.payflow.banking.repository;

import com.payflow.banking.entity.PaymentCharge;
import com.payflow.banking.entity.PaymentChargeStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentChargeRepository extends JpaRepository<PaymentCharge, Long> {

    Optional<PaymentCharge> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentCharge> findByIdAndUserId(Long id, Long userId);

    // [H-5] 취소 처리 시 비관적 락을 획득해 동시 취소 요청에 의한 중복 취소를 방지한다.
    // 동시에 두 취소 요청이 같은 charge에 들어오면 하나만 락을 잡고 처리하며, 나머지는 이미 취소된 상태를 보게 된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PaymentCharge c WHERE c.id = :id AND c.userId = :userId")
    Optional<PaymentCharge> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<PaymentCharge> findTop50ByStatusOrderByUpdatedAtDesc(PaymentChargeStatus status);

    List<PaymentCharge> findTop50ByStatusAndLedgerRecordedFalseAndWalletIdIsNotNullOrderByUpdatedAtDesc(PaymentChargeStatus status);

    long countByStatus(PaymentChargeStatus status);

    long countByLedgerRecordedFalseAndWalletIdIsNotNullAndStatus(PaymentChargeStatus status);
}
