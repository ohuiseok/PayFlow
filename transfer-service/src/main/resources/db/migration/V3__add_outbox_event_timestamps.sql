ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS processing_started_at DATETIME(6) NULL;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS published_at DATETIME(6) NULL;
