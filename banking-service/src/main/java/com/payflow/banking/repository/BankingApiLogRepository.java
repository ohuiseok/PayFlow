package com.payflow.banking.repository;

import com.payflow.banking.entity.BankingApiLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankingApiLogRepository extends JpaRepository<BankingApiLog, Long> {
}
