package com.payflow.ledger.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferEventConsumer {

    private final ObjectMapper objectMapper;
    private final LedgerService ledgerService;

    @KafkaListener(topics = "${topics.transfer-completed:transfer.completed}", groupId = "${spring.kafka.consumer.group-id:ledger-service}")
    public void handleTransferCompleted(String payload) throws JsonProcessingException {
        // Kafka에서 받은 JSON 문자열을 이벤트 record로 바꾼 뒤 서비스에 넘긴다.
        // 컨슈머는 메시지 입출력만 담당하고, 중복 방지와 원장 생성 규칙은 LedgerService에 둔다.
        ledgerService.recordTransfer(objectMapper.readValue(payload, TransferCompletedEvent.class));
    }

    @KafkaListener(topics = "${topics.transfer-failed:transfer.failed}", groupId = "${spring.kafka.consumer.group-id:ledger-service}")
    public void handleTransferFailed(String payload) throws JsonProcessingException {
        ledgerService.recordTransferFailure(objectMapper.readValue(payload, TransferFailedEvent.class));
    }
}
