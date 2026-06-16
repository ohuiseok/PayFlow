package com.payflow.transfer.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.transfer.entity.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${topics.transfer-completed:transfer.completed}")
    private String transferCompletedTopic;

    @Value("${topics.transfer-failed:transfer.failed}")
    private String transferFailedTopic;

    public void publishCompleted(Transfer transfer) {
        // Kafka 메시지 key를 transferId로 두면 같은 송금 이벤트가 같은 파티션으로 가기 쉽다.
        // 순서가 중요한 후속 처리(원장 기록 등)에 유리하다.
        publish(
                transferCompletedTopic,
                transfer.getId().toString(),
                new TransferCompletedEvent(
                        transfer.getId(),
                        transfer.getSenderUserId(),
                        transfer.getReceiverUserId(),
                        transfer.getAmount()
                )
        );
    }

    public void publishFailed(Transfer transfer) {
        // 실패 이벤트도 발행해 두면 알림, 보상 배치, 운영 대시보드 같은 후속 시스템이 같은 사실을 공유할 수 있다.
        publish(
                transferFailedTopic,
                transfer.getId().toString(),
                new TransferFailedEvent(
                        transfer.getId(),
                        transfer.getSenderUserId(),
                        transfer.getReceiverUserId(),
                        transfer.getAmount(),
                        transfer.getStatus().name(),
                        transfer.getFailureReason()
                )
        );
    }

    private void publish(String topic, String key, Object event) {
        try {
            // 내부 이벤트 객체를 JSON 문자열로 직렬화해 Kafka에 보낸다.
            // 서비스 간 이벤트 스키마를 명확히 유지하려면 record 필드 변경도 호환성을 고려해야 한다.
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize transfer event", exception);
        }
    }
}
