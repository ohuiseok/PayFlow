ALTER TABLE payment_charges ADD COLUMN compensation_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE payment_charges ADD COLUMN compensation_failure_reason VARCHAR(500);
ALTER TABLE payment_charges ADD COLUMN compensated_at DATETIME(6);
