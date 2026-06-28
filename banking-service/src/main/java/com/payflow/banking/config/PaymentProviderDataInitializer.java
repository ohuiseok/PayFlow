package com.payflow.banking.config;

import com.payflow.banking.entity.PaymentProvider;
import com.payflow.banking.repository.PaymentProviderRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentProviderDataInitializer implements ApplicationRunner {

    private final PaymentProviderRepository paymentProviderRepository;

    public PaymentProviderDataInitializer(PaymentProviderRepository paymentProviderRepository) {
        this.paymentProviderRepository = paymentProviderRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("TOSS_PAYMENTS", "Toss Payments");
        createIfMissing("OPEN_BANKING", "Open Banking");
    }

    private void createIfMissing(String providerCode, String displayName) {
        if (!paymentProviderRepository.existsByProviderCode(providerCode)) {
            paymentProviderRepository.save(new PaymentProvider(providerCode, displayName));
        }
    }
}
