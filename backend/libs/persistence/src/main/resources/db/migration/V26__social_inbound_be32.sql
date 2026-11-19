-- BE-32: social inbound events (VK Callback / TG webhook / Max webhook). Expand-only.
-- V22 intentionally skipped. V25 reserved for BE-30 (PR #71) when merged.

-- ---------------------------------------------------------------------------
-- channel_event_subscriptions
-- ---------------------------------------------------------------------------
CREATE TABLE channel_event_subscriptions (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL,
    channel_id          UUID NOT NULL,
    network             TEXT NOT NULL,
    mode                TEXT NOT NULL,
    remote_server_id    TEXT,
    secret_enc          BYTEA,
    confirmation_code   TEXT,
    event_types         TEXT[] NOT NULL DEFAULT '{}',
    consumers           TEXT[] NOT NULL DEFAULT '{}',
    status              TEXT NOT NULL DEFAULT 'pending',
    last_event_at       TIMESTAMPTZ,
    last_error          TEXT,
    failure_count       INT NOT NULL DEFAULT 0,
    version             INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT channel_event_subscriptions_channel_uq UNIQUE (channel_id)
);

ALTER TABLE channel_event_subscriptions
    ADD CONSTRAINT channel_event_subscriptions_network_check
    CHECK (network IN ('vk', 'tg', 'max')) NOT VALID;
ALTER TABLE channel_event_subscriptions VALIDATE CONSTRAINT channel_event_subscriptions_network_check;

ALTER TABLE channel_event_subscriptions
    ADD CONSTRAINT channel_event_subscriptions_mode_check
    CHECK (mode IN ('vk_callback', 'tg_platform_bot', 'tg_custom_bot', 'max_webhook')) NOT VALID;
ALTER TABLE channel_event_subscriptions VALIDATE CONSTRAINT channel_event_subscriptions_mode_check;

ALTER TABLE channel_event_subscriptions
    ADD CONSTRAINT channel_event_subscriptions_status_check
    CHECK (status IN ('pending', 'active', 'degraded', 'failed', 'disabled')) NOT VALID;
ALTER TABLE channel_event_subscriptions VALIDATE CONSTRAINT channel_event_subscriptions_status_check;

CREATE INDEX channel_event_subscriptions_workspace_idx
    ON channel_event_subscriptions (workspace_id);

CREATE INDEX channel_event_subscriptions_status_network_idx
    ON channel_event_subscriptions (status, network);

COMMENT ON TABLE channel_event_subscriptions IS 'Per-channel inbound event subscription (VK Callback / TG / Max). One VK callback server per community.';
COMMENT ON COLUMN channel_event_subscriptions.secret_enc IS 'Webhook/callback secret sealed with workspace DEK (ADR-0006)';
COMMENT ON COLUMN channel_event_subscriptions.consumers IS 'Modules consuming events: suggest | antispam | crosspost';

-- ---------------------------------------------------------------------------
-- social_inbound_events (daily partitions by received_at, retention 14d)
-- PK/UNIQUE must include partition key (PostgreSQL rule).
-- Application-level dedup looks up (network, channel_id, dedup_key) across partitions.
-- ---------------------------------------------------------------------------
CREATE TABLE social_inbound_events (
    id              UUID NOT NULL,
    workspace_id    UUID NOT NULL,
    channel_id      UUID NOT NULL,
    network         TEXT NOT NULL,
    kind            TEXT NOT NULL,
    dedup_key       TEXT NOT NULL,
    author_id       TEXT,
    author_is_community BOOLEAN,
    author_is_bot   BOOLEAN,
    text            TEXT,
    remote_ids      JSONB NOT NULL DEFAULT '{}'::jsonb,
    entities        JSONB,
    attachments     JSONB,
    raw             TEXT,
    occurred_at     TIMESTAMPTZ,
    received_at     TIMESTAMPTZ NOT NULL,
    processed_at    TIMESTAMPTZ,
    PRIMARY KEY (id, received_at),
    CONSTRAINT social_inbound_events_dedup_uq UNIQUE (network, channel_id, dedup_key, received_at)
) PARTITION BY RANGE (received_at);

ALTER TABLE social_inbound_events
    ADD CONSTRAINT social_inbound_events_network_check
    CHECK (network IN ('vk', 'tg', 'max')) NOT VALID;
ALTER TABLE social_inbound_events VALIDATE CONSTRAINT social_inbound_events_network_check;

CREATE INDEX social_inbound_events_ws_received_idx
    ON social_inbound_events (workspace_id, received_at DESC);

CREATE INDEX social_inbound_events_dedup_lookup_idx
    ON social_inbound_events (network, channel_id, dedup_key);

-- Seed partitions: yesterday .. +7 days (UTC) relative to migration time.
-- Further days are created by InboundPartitionJob (ShedLock).
DO $$
DECLARE
    d date;
    part_name text;
BEGIN
    FOR d IN SELECT generate_series((CURRENT_DATE - 1), (CURRENT_DATE + 7), interval '1 day')::date LOOP
        part_name := 'social_inbound_events_' || to_char(d, 'YYYY_MM_DD');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF social_inbound_events FOR VALUES FROM (%L) TO (%L)',
            part_name,
            d::timestamptz,
            (d + 1)::timestamptz
        );
    END LOOP;
END $$;

COMMENT ON TABLE social_inbound_events IS 'Dedup inbox for social inbound events; raw truncated to 64 KiB; retention 14 days';

-- ---------------------------------------------------------------------------
-- telegram_bots: nullable columns for custom channel bots
-- ---------------------------------------------------------------------------
ALTER TABLE telegram_bots
    ADD COLUMN IF NOT EXISTS workspace_id UUID,
    ADD COLUMN IF NOT EXISTS channel_id UUID,
    ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'platform';

ALTER TABLE telegram_bots
    ADD CONSTRAINT telegram_bots_kind_check
    CHECK (kind IN ('platform', 'custom')) NOT VALID;
ALTER TABLE telegram_bots VALIDATE CONSTRAINT telegram_bots_kind_check;

CREATE INDEX IF NOT EXISTS telegram_bots_workspace_idx
    ON telegram_bots (workspace_id)
    WHERE workspace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS telegram_bots_channel_idx
    ON telegram_bots (channel_id)
    WHERE channel_id IS NOT NULL;

COMMENT ON COLUMN telegram_bots.kind IS 'platform = shared Posts bot; custom = channel-owned bot (webhook takeover warning)';
COMMENT ON COLUMN telegram_bots.workspace_id IS 'Nullable; set for custom bots bound to a workspace';
COMMENT ON COLUMN telegram_bots.channel_id IS 'Nullable; set for custom bots bound to a channel';

-- ---------------------------------------------------------------------------
-- posts.source_type: widen for suggest / crosspost (shared by BE-34 / BE-36)
-- ---------------------------------------------------------------------------
ALTER TABLE posts
    ADD CONSTRAINT posts_source_type_check_v26
    CHECK (source_type IN (
        'manual', 'grabber', 'rss', 'import', 'api', 'suggest', 'crosspost'
    )) NOT VALID;
ALTER TABLE posts VALIDATE CONSTRAINT posts_source_type_check_v26;
-- squawk-ignore ban-drop-constraint
ALTER TABLE posts DROP CONSTRAINT posts_source_type_check;
