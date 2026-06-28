package com.payflow.settlement.batch;

import com.payflow.settlement.service.DailySettlementJobService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailySettlementScheduler {
    private final DailySettlementJobService jobService;

    @Value("${settlement.zone:Asia/Seoul}")
    private String settlementZone;

    @Scheduled(cron = "${settlement.daily-cron:0 0 1 * * *}", zone = "${settlement.zone:Asia/Seoul}")
    public void settlePreviousDay() {
        try { jobService.run(LocalDate.now(ZoneId.of(settlementZone)).minusDays(1)); }
        catch (Exception exception) { log.error("Daily payment settlement failed", exception); }
    }
}
