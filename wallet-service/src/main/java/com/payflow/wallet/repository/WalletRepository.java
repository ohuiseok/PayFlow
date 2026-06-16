package com.payflow.wallet.repository;

import com.payflow.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    boolean existsByUserId(Long userId);

    Optional<Wallet> findByUserId(Long userId);

    // SELECT ... FOR UPDATE에 해당하는 비관적 쓰기 락을 건다.
    // 같은 지갑의 잔액을 바꾸는 트랜잭션들이 한 줄로 서서 처리되므로 동시 출금에서도 잔액 정합성을 지킬 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :walletId")
    Optional<Wallet> findByIdForUpdate(@Param("walletId") Long walletId);
}
