package com.payflow.banking.repository;

import com.payflow.banking.entity.OpenBankingAuthorization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenBankingAuthorizationRepository extends JpaRepository<OpenBankingAuthorization, Long> {

    Optional<OpenBankingAuthorization> findByState(String state);
}
