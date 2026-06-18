package com.payflow.transfer.controller;

import com.payflow.transfer.dto.OutboxSummaryResponse;
import com.payflow.transfer.service.OutboxMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers/outbox")
@RequiredArgsConstructor
public class OutboxMonitoringController {

    private final OutboxMonitoringService outboxMonitoringService;

    @GetMapping("/summary")
    public OutboxSummaryResponse getSummary() {
        return outboxMonitoringService.getSummary();
    }
}
