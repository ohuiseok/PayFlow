package com.payflow.banking.repository;

import com.payflow.banking.entity.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderRepository extends JpaRepository<PaymentProvider, Long> {

    boolean existsByProviderCode(String providerCode);
}
