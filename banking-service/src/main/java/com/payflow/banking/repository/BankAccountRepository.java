package com.payflow.banking.repository;

import com.payflow.banking.entity.BankAccount;
import com.payflow.banking.entity.BankAccountStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    boolean existsByUserIdAndBankCodeAndAccountNumber(Long userId, String bankCode, String accountNumber);

    boolean existsByUserIdAndFintechUseNum(Long userId, String fintechUseNum);

    boolean existsByUserIdAndStatus(Long userId, BankAccountStatus status);

    List<BankAccount> findByUserIdAndStatusOrderByIdDesc(Long userId, BankAccountStatus status);

    Optional<BankAccount> findByIdAndUserIdAndStatus(Long id, Long userId, BankAccountStatus status);
}
