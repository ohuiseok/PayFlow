package com.payflow.banking.repository;

import com.payflow.banking.entity.TossPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TossPaymentEventRepository extends JpaRepository<TossPaymentEvent, Long> {

    boolean existsByEventIdempotencyKey(String eventIdempotencyKey);
}
