-- [H-1] wallet-service 초기 스키마
-- Wallet 엔티티와 WalletTransaction 엔티티를 기반으로 한 마이그레이션이다.
-- wallet_transactions.uk_wallet_transaction_reference unique 제약은 멱등성 보장의 핵심이다.

CREATE TABLE IF NOT EXISTS wallets (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    balance     DECIMAL(19, 0)  NOT NULL,
    status      VARCHAR(30)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    wallet_id        BIGINT          NOT NULL,
    transaction_type VARCHAR(30)     NOT NULL,
    amount           DECIMAL(19, 0)  NOT NULL,
    balance_after    DECIMAL(19, 0)  NOT NULL,
    reference_type   VARCHAR(50)     NOT NULL,
    reference_id     VARCHAR(100)    NOT NULL,
    created_at       DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wallet_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT uk_wallet_transaction_reference UNIQUE (wallet_id, transaction_type, reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions (wallet_id);
