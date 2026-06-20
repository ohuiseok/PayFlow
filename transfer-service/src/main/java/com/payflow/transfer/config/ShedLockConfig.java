package com.payflow.transfer.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * [M-8] ShedLock 설정이다.
 *
 * <p>OutboxEventRelay는 @Scheduled로 2초마다 실행되는데,
 * transfer-service 인스턴스가 여러 개 뜨면 같은 outbox 이벤트를 동시에 발행할 수 있다.
 * OutboxEventRepository.claimPublishableEvent()로 개별 이벤트 단위 중복은 막을 수 있지만,
 * 스케줄러 자체가 동시에 수십 개 실행되면 불필요한 DB 부하와 Kafka 발행 경합이 발생한다.</p>
 *
 * <p>ShedLock은 DB의 shedlock 테이블을 활용해 하나의 인스턴스만 스케줄러를 실행하도록 보장한다.
 * MySQL 기반 JDBC 잠금이므로 Redis 없이 기존 MySQL에 의존한다.</p>
 *
 * <p>shedlock 테이블 DDL (Flyway로 관리해야 한다):
 * <pre>
 * CREATE TABLE shedlock (
 *     name        VARCHAR(64)  NOT NULL,
 *     lock_until  TIMESTAMP(3) NOT NULL,
 *     locked_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 *     locked_by   VARCHAR(255) NOT NULL,
 *     PRIMARY KEY (name)
 * );
 * </pre>
 * </p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
