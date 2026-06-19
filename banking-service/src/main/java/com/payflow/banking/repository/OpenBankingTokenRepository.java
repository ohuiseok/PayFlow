package com.payflow.banking.repository;

import com.payflow.banking.entity.OpenBankingToken;
import com.payflow.banking.entity.OpenBankingTokenType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenBankingTokenRepository extends JpaRepository<OpenBankingToken, Long> {

    Optional<OpenBankingToken> findByUserIdAndTokenType(Long userId, OpenBankingTokenType tokenType);
}
