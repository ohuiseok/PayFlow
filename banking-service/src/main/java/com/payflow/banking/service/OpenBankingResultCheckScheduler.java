package com.payflow.banking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenBankingResultCheckScheduler {

    private final BankingService bankingService;

    @Scheduled(fixedDelayString = "${openbanking.result-check-fixed-delay-ms:60000}")
    public void checkDueTransferResults() {
        bankingService.checkDueTransferResults();
    }
}
