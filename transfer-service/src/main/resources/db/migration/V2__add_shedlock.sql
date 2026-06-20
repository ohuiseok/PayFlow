-- [M-8] ShedLock 분산 스케줄러 락을 위한 테이블이다.
-- 다중 인스턴스 환경에서 OutboxEventRelay 등 스케줄러가 중복 실행되지 않도록 DB 잠금을 제공한다.
-- ShedLock 라이브러리가 이 테이블을 직접 관리한다.

CREATE TABLE IF NOT EXISTS shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
