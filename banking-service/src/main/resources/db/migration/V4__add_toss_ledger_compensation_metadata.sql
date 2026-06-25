ALTER TABLE payment_charges ADD COLUMN ledger_recorded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payment_charges ADD COLUMN ledger_record_type VARCHAR(40);
ALTER TABLE payment_charges ADD COLUMN ledger_failure_reason VARCHAR(500);
ALTER TABLE payment_charges ADD COLUMN ledger_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE payment_charges ADD COLUMN ledger_recorded_at DATETIME(6);
