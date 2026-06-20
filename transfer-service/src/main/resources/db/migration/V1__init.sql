-- [H-1] transfer-service 초기 스키마
-- Transfer 엔티티와 OutboxEvent 엔티티를 기반으로 한 마이그레이션이다.
-- idempotency_key unique 제약은 중복 송금 방지의 핵심이다.

CREATE TABLE IF NOT EXISTS transfers (
    id                           BIGINT          NOT NULL AUTO_INCREMENT,
    sender_user_id               BIGINT          NOT NULL,
    receiver_user_id             BIGINT          NOT NULL,
    amount                       DECIMAL(19, 0)  NOT NULL,
    status                       VARCHAR(30)     NOT NULL,
    idempotency_key              VARCHAR(120)    NOT NULL,
    request_hash                 VARCHAR(64)     NOT NULL,
    sender_wallet_id             BIGINT,
    receiver_wallet_id           BIGINT,
    failure_reason               VARCHAR(500),
    compensation_retry_count     INT             NOT NULL DEFAULT 0,
    compensation_failure_reason  VARCHAR(500),
    compensated_at               DATETIME(6),
    created_at                   DATETIME(6)     NOT NULL,
    updated_at                   DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transfers_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [M-3] 송금 내역 조회 성능을 위한 인덱스 (발신자/수신자 기준 조회)
CREATE INDEX idx_transfers_sender_user_id   ON transfers (sender_user_id);
CREATE INDEX idx_transfers_receiver_user_id ON transfers (receiver_user_id);
CREATE INDEX idx_transfers_status           ON transfers (status);

CREATE TABLE IF NOT EXISTS outbox_events (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    event_id    VARCHAR(36)     NOT NULL,
    topic       VARCHAR(120)    NOT NULL,
    event_key   VARCHAR(120)    NOT NULL,
    payload     TEXT            NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    retry_count INT             NOT NULL DEFAULT 0,
    last_error  VARCHAR(500),
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_outbox_events_status ON outbox_events (status);
