package com.payflow.banking.openbanking;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenBankingProperties.class)
public class OpenBankingConfig {
}
