package com.payflow.transfer.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.transfer.outbox.OutboxEvent;
import com.payflow.transfer.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "outbox.publisher.max-retries=2")
@Transactional
class OutboxMonitoringControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void getSummaryReturnsOutboxSummary() throws Exception {
        outboxEventRepository.save(new OutboxEvent("transfer.completed", "1", "{}"));
        OutboxEvent failed = new OutboxEvent("transfer.failed", "2", "{}");
        failed.markFailed("kafka down");
        outboxEventRepository.save(failed);

        mockMvc.perform(get("/transfers/outbox/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.retryableFailureCount").value(1))
                .andExpect(jsonPath("$.retryExhaustedCount").value(0))
                .andExpect(jsonPath("$.oldestPendingEventAgeSeconds").exists())
                .andExpect(jsonPath("$.oldestPendingEventCreatedAt").exists())
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'PENDING')].count").value(1))
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'FAILED')].count").value(1));
    }
}
