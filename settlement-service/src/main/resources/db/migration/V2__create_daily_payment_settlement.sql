CREATE TABLE settlement_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    charge_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    payment_key VARCHAR(200),
    amount DECIMAL(19,0) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    ledger_source_type VARCHAR(40) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_transactions_event UNIQUE (event_id),
    INDEX idx_settlement_transactions_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    discrepancy_count BIGINT NOT NULL DEFAULT 0,
    gross_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
    cancel_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
    fee_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
    expected_net_amount DECIMAL(19,0) NOT NULL DEFAULT 0,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    failure_reason VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_runs_business_date UNIQUE (business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_run_id BIGINT NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    charge_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    expected_amount DECIMAL(19,0) NOT NULL,
    ledger_amount DECIMAL(19,0),
    ledger_entry_id BIGINT,
    status VARCHAR(30) NOT NULL,
    discrepancy_reason VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_settlement_items_event UNIQUE (event_id),
    CONSTRAINT fk_settlement_items_run FOREIGN KEY (settlement_run_id) REFERENCES settlement_runs(id),
    INDEX idx_settlement_items_run_status (settlement_run_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
