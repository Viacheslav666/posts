-- Expand idempotency_requests for explicit TTL (API Idempotency-Key, 24h).
ALTER TABLE idempotency_requests
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

UPDATE idempotency_requests
SET expires_at = created_at + INTERVAL '24 hours'
WHERE expires_at IS NULL;

ALTER TABLE idempotency_requests
    ALTER COLUMN expires_at SET DEFAULT (now() + INTERVAL '24 hours');

CREATE INDEX IF NOT EXISTS idempotency_requests_expires_at_idx
    ON idempotency_requests (expires_at);
