package com.payflow.settlement.service;

import com.payflow.settlement.dto.SettlementRunResponse;
import com.payflow.settlement.entity.SettlementRunStatus;
import com.payflow.settlement.repository.SettlementRunRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailySettlementJobService {
    private final JobOperator jobOperator;
    private final Job dailyPaymentSettlementJob;
    private final SettlementRunRepository runRepository;

    public SettlementRunResponse run(LocalDate businessDate) throws Exception {
        var existing = runRepository.findByBusinessDate(businessDate);
        if (existing.isPresent() && (existing.get().getStatus() == SettlementRunStatus.COMPLETED
                || existing.get().getStatus() == SettlementRunStatus.WITH_DISCREPANCY)) {
            return SettlementRunResponse.from(existing.get());
        }
        jobOperator.start(dailyPaymentSettlementJob, new JobParametersBuilder()
                .addString("businessDate", businessDate.toString())
                .toJobParameters());
        return SettlementRunResponse.from(runRepository.findByBusinessDate(businessDate).orElseThrow());
    }

    public SettlementRunResponse get(LocalDate businessDate) {
        return runRepository.findByBusinessDate(businessDate).map(SettlementRunResponse::from).orElse(null);
    }
}
