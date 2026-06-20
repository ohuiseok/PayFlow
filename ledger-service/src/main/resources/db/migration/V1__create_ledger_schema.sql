CREATE TABLE ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NOT NULL,
    entry_type VARCHAR(40) NOT NULL,
    sender_user_id BIGINT NULL,
    receiver_user_id BIGINT NULL,
    amount DECIMAL(19, 0) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ledger_entries_transfer_id UNIQUE (transfer_id),
    CONSTRAINT uk_ledger_entries_source UNIQUE (source_type, source_id)
);

CREATE TABLE ledger_lines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ledger_entry_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    account_code VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 0) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_lines_entry FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entries (id)
);

CREATE TABLE transfer_failure_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    receiver_user_id BIGINT NOT NULL,
    amount DECIMAL(19, 0) NOT NULL,
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transfer_failure_events_transfer_id UNIQUE (transfer_id)
);
