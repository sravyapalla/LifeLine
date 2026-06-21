ALTER TABLE outbox_events
    ADD COLUMN publish_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_publish_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_publish_error TEXT,
    ADD COLUMN next_publish_attempt_at TIMESTAMPTZ,
    ADD CONSTRAINT outbox_publish_attempts_chk CHECK (publish_attempts >= 0);

CREATE INDEX outbox_ready_retry_idx
    ON outbox_events (next_publish_attempt_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX outbox_failed_idx
    ON outbox_events (created_at)
    WHERE published_at IS NULL AND last_publish_error IS NOT NULL;
