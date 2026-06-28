package com.payflow.settlement.repository;

import com.payflow.settlement.entity.SettlementRun;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRunRepository extends JpaRepository<SettlementRun, Long> {
    Optional<SettlementRun> findByBusinessDate(LocalDate businessDate);
}
