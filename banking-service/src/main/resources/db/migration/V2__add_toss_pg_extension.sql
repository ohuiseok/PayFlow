ALTER TABLE bank_accounts
    ADD COLUMN provider_code VARCHAR(30),
    ADD COLUMN open_banking_authorization_id BIGINT,
    ADD COLUMN fintech_use_num_encrypted VARCHAR(2000),
    ADD COLUMN account_alias VARCHAR(100),
    ADD COLUMN linked_at DATETIME(6),
    ADD COLUMN last_synced_at DATETIME(6);

CREATE TABLE payment_providers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_code VARCHAR(30) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_providers_provider_code UNIQUE (provider_code)
);

INSERT INTO payment_providers (provider_code, display_name, status, created_at, updated_at)
VALUES
    ('TOSS_PAYMENTS', 'Toss Payments', 'ACTIVE', NOW(6), NOW(6)),
    ('OPEN_BANKING', 'Open Banking', 'ACTIVE', NOW(6), NOW(6));

CREATE TABLE payment_charges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider_code VARCHAR(30) NOT NULL,
    charge_method VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 0) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    wallet_id BIGINT,
    wallet_transaction_id BIGINT,
    failure_code VARCHAR(100),
    failure_reason VARCHAR(500),
    completed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_charges_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payment_charges_wallet_transaction_id UNIQUE (wallet_transaction_id)
);

CREATE INDEX idx_payment_charges_user_status ON payment_charges (user_id, status);

CREATE TABLE toss_payment_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_charge_id BIGINT NOT NULL,
    toss_order_id VARCHAR(64) NOT NULL,
    payment_key VARCHAR(200),
    order_name VARCHAR(100) NOT NULL,
    method VARCHAR(50),
    toss_status VARCHAR(40) NOT NULL,
    total_amount DECIMAL(19, 0) NOT NULL,
    balance_amount DECIMAL(19, 0),
    approved_at DATETIME(6),
    receipt_url VARCHAR(500),
    checkout_url VARCHAR(500),
    raw_response_json TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_toss_payment_orders_charge UNIQUE (payment_charge_id),
    CONSTRAINT uk_toss_payment_orders_order_id UNIQUE (toss_order_id),
    CONSTRAINT uk_toss_payment_orders_payment_key UNIQUE (payment_key)
);

CREATE TABLE toss_payment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    toss_payment_order_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payment_key VARCHAR(200),
    transaction_key VARCHAR(64),
    toss_status VARCHAR(40),
    event_idempotency_key VARCHAR(255),
    payload_json TEXT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_toss_payment_events_idempotency_key UNIQUE (event_idempotency_key)
);

CREATE INDEX idx_toss_payment_events_order ON toss_payment_events (toss_payment_order_id);

CREATE TABLE open_banking_authorizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    state VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    user_seq_no VARCHAR(50),
    access_token_encrypted VARCHAR(2000),
    refresh_token_encrypted VARCHAR(2000),
    token_expires_at DATETIME(6),
    failure_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_open_banking_authorizations_state UNIQUE (state)
);

CREATE INDEX idx_open_banking_authorizations_user_status ON open_banking_authorizations (user_id, status);
