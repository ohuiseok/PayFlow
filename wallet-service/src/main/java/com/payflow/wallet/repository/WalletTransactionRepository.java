package com.payflow.wallet.repository;

import com.payflow.wallet.entity.WalletTransaction;
import com.payflow.wallet.entity.WalletTransactionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByWalletIdAndTransactionTypeAndReferenceTypeAndReferenceId(
            Long walletId,
            WalletTransactionType transactionType,
            String referenceType,
            String referenceId
    );
}
