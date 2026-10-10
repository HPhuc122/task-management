CREATE TABLE api_idempotency (
    actor_id BIGINT NOT NULL,
    operation VARCHAR(60) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_id, operation, request_key)
);

CREATE TABLE notification_outbox (
    event_id UUID PRIMARY KEY,
    task_id BIGINT NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    task_title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (next_attempt_at, created_at) WHERE published_at IS NULL;
