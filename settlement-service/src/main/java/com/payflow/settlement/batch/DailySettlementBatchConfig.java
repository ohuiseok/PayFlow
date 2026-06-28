package com.payflow.settlement.batch;

import com.payflow.settlement.entity.SettlementTransaction;
import com.payflow.settlement.repository.SettlementItemRepository;
import com.payflow.settlement.repository.SettlementRunRepository;
import com.payflow.settlement.repository.SettlementTransactionRepository;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DailySettlementBatchConfig {
    @Bean
    @StepScope
    RepositoryItemReader<SettlementTransaction> settlementTransactionReader(
            SettlementTransactionRepository repository,
            @Value("#{jobParameters['businessDate']}") String businessDate
    ) {
        LocalDate date = LocalDate.parse(businessDate);
        return new RepositoryItemReaderBuilder<SettlementTransaction>()
                .name("settlementTransactionReader")
                .repository(repository)
                .methodName("findByOccurredAtGreaterThanEqualAndOccurredAtLessThan")
                .arguments(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .pageSize(100)
                .sorts(Map.of("id", Sort.Direction.ASC))
                .saveState(true)
                .build();
    }

    @Bean
    @StepScope
    ItemWriter<SettlementReconciliationResult> settlementItemWriter(
            SettlementRunRepository runRepository,
            SettlementItemRepository itemRepository,
            @Value("#{jobParameters['businessDate']}") String businessDate
    ) {
        return new SettlementReconciliationWriter(LocalDate.parse(businessDate), runRepository, itemRepository);
    }

    @Bean
    Step dailySettlementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<SettlementTransaction> settlementTransactionReader,
            SettlementReconciliationProcessor processor,
            ItemWriter<SettlementReconciliationResult> settlementItemWriter
    ) {
        return new StepBuilder("dailySettlementStep", jobRepository)
                .<SettlementTransaction, SettlementReconciliationResult>chunk(100)
                .transactionManager(transactionManager)
                .reader(settlementTransactionReader)
                .processor(processor)
                .writer(settlementItemWriter)
                .build();
    }

    @Bean
    Job dailyPaymentSettlementJob(JobRepository jobRepository, Step dailySettlementStep, DailySettlementJobListener listener) {
        return new JobBuilder("dailyPaymentSettlementJob", jobRepository)
                .listener(listener)
                .start(dailySettlementStep)
                .build();
    }
}
