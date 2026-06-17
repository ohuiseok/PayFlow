package com.payflow.banking.repository;

import com.payflow.banking.entity.BankingTransfer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankingTransferRepository extends JpaRepository<BankingTransfer, Long> {

    Optional<BankingTransfer> findByIdempotencyKey(String idempotencyKey);

    Optional<BankingTransfer> findByIdAndUserId(Long id, Long userId);
}
