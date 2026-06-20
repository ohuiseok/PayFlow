package com.payflow.banking.repository;

import com.payflow.banking.entity.PaymentCharge;
import com.payflow.banking.entity.PaymentChargeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentChargeRepository extends JpaRepository<PaymentCharge, Long> {

    Optional<PaymentCharge> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentCharge> findByIdAndUserId(Long id, Long userId);

    List<PaymentCharge> findTop50ByStatusOrderByUpdatedAtDesc(PaymentChargeStatus status);

    List<PaymentCharge> findTop50ByStatusAndLedgerRecordedFalseAndWalletIdIsNotNullOrderByUpdatedAtDesc(PaymentChargeStatus status);

    long countByStatus(PaymentChargeStatus status);

    long countByLedgerRecordedFalseAndWalletIdIsNotNullAndStatus(PaymentChargeStatus status);
}
