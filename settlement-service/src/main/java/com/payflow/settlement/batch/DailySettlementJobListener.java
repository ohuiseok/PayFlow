package com.payflow.settlement.batch;

import com.payflow.settlement.entity.ReconciliationStatus;
import com.payflow.settlement.entity.SettlementRun;
import com.payflow.settlement.event.PaymentSettlementEventType;
import com.payflow.settlement.repository.SettlementItemRepository;
import com.payflow.settlement.repository.SettlementRunRepository;
import com.payflow.settlement.repository.SettlementTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DailySettlementJobListener implements JobExecutionListener {
    private final SettlementRunRepository runRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementTransactionRepository transactionRepository;

    @Value("${settlement.fee-rate:0.027}")
    private BigDecimal feeRate;

    @Override @Transactional
    public void beforeJob(JobExecution jobExecution) {
        LocalDate date = businessDate(jobExecution);
        SettlementRun run = runRepository.findByBusinessDate(date).orElseGet(() -> new SettlementRun(date));
        run.start();
        runRepository.save(run);
    }

    @Override @Transactional
    public void afterJob(JobExecution jobExecution) {
        LocalDate date = businessDate(jobExecution);
        SettlementRun run = runRepository.findByBusinessDate(date).orElseThrow();
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            run.fail(jobExecution.getExitStatus().getExitDescription());
            return;
        }
        var transactions = transactionRepository.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), Pageable.unpaged()).getContent();
        BigDecimal gross = transactions.stream().filter(t -> t.getTransactionType() == PaymentSettlementEventType.CHARGE)
                .map(t -> t.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cancel = transactions.stream().filter(t -> t.getTransactionType() == PaymentSettlementEventType.CANCEL)
                .map(t -> t.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = gross.multiply(feeRate).setScale(0, RoundingMode.HALF_UP);
        long discrepancies = itemRepository.countBySettlementRunIdAndStatusNot(run.getId(), ReconciliationStatus.MATCHED);
        run.complete(transactions.size(), discrepancies, gross, cancel, fee);
    }

    private LocalDate businessDate(JobExecution execution) {
        return LocalDate.parse(execution.getJobParameters().getString("businessDate"));
    }
}
