package com.payflow.banking.repository;

import com.payflow.banking.entity.TossPaymentOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TossPaymentOrderRepository extends JpaRepository<TossPaymentOrder, Long> {

    Optional<TossPaymentOrder> findByPaymentChargeId(Long paymentChargeId);

    Optional<TossPaymentOrder> findByTossOrderId(String tossOrderId);

    Optional<TossPaymentOrder> findByPaymentKey(String paymentKey);
}
