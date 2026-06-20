ALTER TABLE payment_charges
    ADD COLUMN compensation_retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN compensation_failure_reason VARCHAR(500),
    ADD COLUMN compensated_at DATETIME(6);
