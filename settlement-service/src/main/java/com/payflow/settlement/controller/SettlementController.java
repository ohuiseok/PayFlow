package com.payflow.settlement.controller;

import com.payflow.settlement.dto.SettlementRunResponse;
import com.payflow.settlement.service.DailySettlementJobService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settlements/daily")
@RequiredArgsConstructor
public class SettlementController {
    private final DailySettlementJobService jobService;

    @PostMapping("/{businessDate}")
    public SettlementRunResponse run(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) throws Exception {
        return jobService.run(businessDate);
    }

    @GetMapping("/{businessDate}")
    public ResponseEntity<SettlementRunResponse> get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        SettlementRunResponse response = jobService.get(businessDate);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }
}
