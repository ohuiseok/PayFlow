CREATE TABLE bank_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    bank_code VARCHAR(20) NOT NULL,
    account_number VARCHAR(80) NOT NULL,
    account_number_masked VARCHAR(80) NOT NULL,
    account_holder_name VARCHAR(100) NOT NULL,
    fintech_use_num VARCHAR(30),
    user_seq_no VARCHAR(20),
    bank_name VARCHAR(100),
    inquiry_agree_yn VARCHAR(5),
    transfer_agree_yn VARCHAR(5),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bank_accounts_user_bank_account UNIQUE (user_id, bank_code, account_number)
);

CREATE INDEX idx_bank_accounts_user_status ON bank_accounts (user_id, status);
CREATE INDEX idx_bank_accounts_user_fintech_use_num ON bank_accounts (user_id, fintech_use_num);

CREATE TABLE banking_transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    wallet_id BIGINT,
    amount DECIMAL(19, 0) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    bank_tran_id VARCHAR(80) NOT NULL,
    bank_tran_date VARCHAR(8),
    tran_dtime VARCHAR(14),
    api_tran_id VARCHAR(80),
    api_response_code VARCHAR(20),
    bank_response_code VARCHAR(20),
    wallet_reference_type VARCHAR(50),
    wallet_reference_id VARCHAR(100),
    result_check_count INT NOT NULL DEFAULT 0,
    next_result_check_at DATETIME(6),
    last_result_checked_at DATETIME(6),
    completed_at DATETIME(6),
    wallet_transaction_id BIGINT,
    failure_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_banking_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_banking_transfers_bank_tran_id UNIQUE (bank_tran_id)
);

CREATE INDEX idx_banking_transfers_user ON banking_transfers (user_id);
CREATE INDEX idx_banking_transfers_status_next_check ON banking_transfers (status, next_result_check_at);

CREATE TABLE banking_api_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    banking_transfer_id BIGINT,
    api_name VARCHAR(50) NOT NULL,
    request_id VARCHAR(100),
    http_status INT,
    api_response_code VARCHAR(20),
    api_tran_id VARCHAR(80),
    bank_response_code VARCHAR(20),
    request_keys VARCHAR(1000),
    response_keys VARCHAR(1000),
    error_message VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_banking_api_logs_transfer ON banking_api_logs (banking_transfer_id);
CREATE INDEX idx_banking_api_logs_request_id ON banking_api_logs (request_id);

CREATE TABLE open_banking_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    token_type VARCHAR(20) NOT NULL,
    user_seq_no VARCHAR(30),
    client_use_code VARCHAR(20),
    access_token_encrypted VARCHAR(2000) NOT NULL,
    refresh_token_encrypted VARCHAR(2000),
    scope VARCHAR(100),
    expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_open_banking_tokens_user_type UNIQUE (user_id, token_type)
);

CREATE INDEX idx_open_banking_tokens_user_type ON open_banking_tokens (user_id, token_type);
