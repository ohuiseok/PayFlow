SET @schema_name = DATABASE();

SET @add_processing_started_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE outbox_events ADD COLUMN processing_started_at DATETIME(6) NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'outbox_events'
      AND column_name = 'processing_started_at'
);
PREPARE add_processing_started_at_stmt FROM @add_processing_started_at;
EXECUTE add_processing_started_at_stmt;
DEALLOCATE PREPARE add_processing_started_at_stmt;

SET @add_published_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE outbox_events ADD COLUMN published_at DATETIME(6) NULL',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'outbox_events'
      AND column_name = 'published_at'
);
PREPARE add_published_at_stmt FROM @add_published_at;
EXECUTE add_published_at_stmt;
DEALLOCATE PREPARE add_published_at_stmt;
