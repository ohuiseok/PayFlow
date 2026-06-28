package com.payflow.settlement.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.settlement.entity.SettlementTransaction;
import com.payflow.settlement.repository.SettlementTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentSettlementEventConsumer {
    private final ObjectMapper objectMapper;
    private final SettlementTransactionRepository transactionRepository;

    @KafkaListener(topics = "${topics.payment-settlement:payment.settlement}", groupId = "${spring.kafka.consumer.group-id:settlement-service}")
    @Transactional
    public void consume(String payload) throws JsonProcessingException {
        PaymentSettlementEvent event = objectMapper.readValue(payload, PaymentSettlementEvent.class);
        if (transactionRepository.findByEventId(event.eventId()).isPresent()) return;
        try { transactionRepository.save(new SettlementTransaction(event)); }
        catch (DataIntegrityViolationException ignored) { /* Kafka redelivery raced with an existing insert. */ }
    }
}
