-- BE-11: Notifications delivery settings, bindings, deliveries (expand-only).
-- CHECKs via NOT VALID + VALIDATE (squawk-safe).

-- ---------------------------------------------------------------------------
-- Expand in-app notifications
-- ---------------------------------------------------------------------------
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS workspace_id UUID;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS dedupe_key TEXT;

CREATE INDEX IF NOT EXISTS notifications_workspace_created_idx
    ON notifications (workspace_id, created_at DESC)
    WHERE workspace_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS notifications_user_dedupe_uq
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Per-user notification settings (type × channel matrix in preferences jsonb)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_settings (
    user_id                 UUID PRIMARY KEY REFERENCES users (id),
    timezone                TEXT NOT NULL DEFAULT 'Europe/Moscow',
    quiet_hours_enabled     BOOLEAN NOT NULL DEFAULT false,
    quiet_hours_start       TIME,
    quiet_hours_end         TIME,
    preferences             JSONB NOT NULL DEFAULT '{}'::jsonb,
    email_unsubscribed_all  BOOLEAN NOT NULL DEFAULT false,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE notification_settings
    ADD CONSTRAINT notification_settings_tz_len_check
    CHECK (char_length(timezone) BETWEEN 1 AND 64) NOT VALID;
ALTER TABLE notification_settings VALIDATE CONSTRAINT notification_settings_tz_len_check;

-- ---------------------------------------------------------------------------
-- Telegram notification-bot account link (deep-link code)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_telegram_links (
    user_id              UUID PRIMARY KEY REFERENCES users (id),
    chat_id              BIGINT,
    username             TEXT,
    link_code_hash       TEXT,
    link_code_expires_at TIMESTAMPTZ,
    linked_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS notification_telegram_links_code_uq
    ON notification_telegram_links (link_code_hash)
    WHERE link_code_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS notification_telegram_links_chat_uq
    ON notification_telegram_links (chat_id)
    WHERE chat_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Web Push (VAPID) subscriptions
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_web_push_subscriptions (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    endpoint    TEXT NOT NULL,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS notification_web_push_endpoint_uq
    ON notification_web_push_subscriptions (user_id, endpoint);

CREATE INDEX IF NOT EXISTS notification_web_push_user_idx
    ON notification_web_push_subscriptions (user_id);

-- ---------------------------------------------------------------------------
-- Delivery attempts (idempotent per dedupe_key × channel)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_deliveries (
    id                    UUID PRIMARY KEY,
    notification_id       UUID REFERENCES notifications (id),
    user_id               UUID NOT NULL REFERENCES users (id),
    channel               TEXT NOT NULL,
    event_type            TEXT NOT NULL,
    dedupe_key            TEXT NOT NULL,
    status                TEXT NOT NULL DEFAULT 'pending',
    attempt               INT NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMPTZ,
    last_error            TEXT,
    provider_message_id   TEXT,
    payload               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at               TIMESTAMPTZ
);

ALTER TABLE notification_deliveries
    ADD CONSTRAINT notification_deliveries_channel_check
    CHECK (channel IN ('email', 'telegram', 'web_push', 'in_app', 'ws')) NOT VALID;
ALTER TABLE notification_deliveries VALIDATE CONSTRAINT notification_deliveries_channel_check;

ALTER TABLE notification_deliveries
    ADD CONSTRAINT notification_deliveries_status_check
    CHECK (status IN ('pending', 'sent', 'failed', 'skipped', 'dead')) NOT VALID;
ALTER TABLE notification_deliveries VALIDATE CONSTRAINT notification_deliveries_status_check;

CREATE UNIQUE INDEX IF NOT EXISTS notification_deliveries_dedupe_uq
    ON notification_deliveries (dedupe_key, channel);

CREATE INDEX IF NOT EXISTS notification_deliveries_pending_idx
    ON notification_deliveries (status, next_attempt_at)
    WHERE status IN ('pending', 'failed');

-- ---------------------------------------------------------------------------
-- Email unsubscribe tokens (one-click / link in letter)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_unsubscribe_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id),
    token_hash   TEXT NOT NULL,
    event_type   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at      TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS notification_unsubscribe_token_uq
    ON notification_unsubscribe_tokens (token_hash);

CREATE INDEX IF NOT EXISTS notification_unsubscribe_user_idx
    ON notification_unsubscribe_tokens (user_id);
