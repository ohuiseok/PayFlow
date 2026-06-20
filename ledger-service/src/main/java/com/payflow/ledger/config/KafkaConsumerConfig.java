package com.payflow.ledger.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * [H-4] Kafka 컨슈머 에러 처리 및 DLT(Dead Letter Topic) 설정이다.
 *
 * <p>원장 기록 실패 시 동작 방식:
 * <ol>
 *   <li>1초 간격으로 최대 3회 재시도한다.</li>
 *   <li>3회 모두 실패하면 원본 토픽명에 ".DLT"를 붙인 토픽으로 메시지를 이동한다.
 *       (예: transfer.completed → transfer.completed.DLT)</li>
 *   <li>DLT 메시지는 별도 모니터링 또는 수동 재처리 배치로 소비한다.</li>
 * </ol>
 * </p>
 *
 * <p>이 설정이 없으면 컨슈머가 예외를 던졌을 때 무한 재시도하거나 메시지를 버릴 수 있다.</p>
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /**
     * DLT로 메시지를 전달하는 에러 핸들러.
     * KafkaTemplate은 auto-configured bean을 주입받아 DLT에 발행한다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 재시도 실패 메시지를 {원본토픽}.DLT 토픽으로 이동한다.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    log.error("[Kafka-DLT] 메시지 처리 최종 실패. topic={}, offset={}, payload={}, error={}",
                            record.topic(), record.offset(), record.value(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT",
                            record.partition()
                    );
                }
        );

        // 1초 간격으로 최대 3회 재시도 후 DLT로 보낸다.
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * 에러 핸들러를 적용한 KafkaListenerContainerFactory.
     * @KafkaListener가 이 팩토리를 사용하도록 설정한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
