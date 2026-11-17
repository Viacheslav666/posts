-- BE-7: envelope DEKs, connect OAuth state, Telegram bot pool, channel bot pin.
-- bot_id is a soft reference (no inline FK) to avoid squawk adding-foreign-key-constraint
-- on a live channels table; integrity enforced in ChannelServiceImpl.

CREATE TABLE workspace_data_keys (
    workspace_id  UUID PRIMARY KEY REFERENCES workspaces (id),
    dek_enc       BYTEA NOT NULL,
    key_version   INT NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_at    TIMESTAMPTZ
);

CREATE TABLE channel_connect_states (
    state          TEXT PRIMARY KEY,
    workspace_id   UUID NOT NULL REFERENCES workspaces (id),
    project_id     UUID REFERENCES projects (id),
    network        TEXT NOT NULL,
    user_id        UUID NOT NULL REFERENCES users (id),
    code_verifier  TEXT,
    redirect_uri   TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT channel_connect_states_network_check
        CHECK (network IN ('vk', 'tg', 'ok', 'max', 'rutube'))
);

CREATE INDEX channel_connect_states_expires_idx ON channel_connect_states (expires_at);

CREATE TABLE telegram_bots (
    id               UUID PRIMARY KEY,
    username         TEXT NOT NULL,
    token_enc        BYTEA NOT NULL,
    webhook_secret   TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'active',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT telegram_bots_username_uq UNIQUE (username),
    CONSTRAINT telegram_bots_status_check CHECK (status IN ('active', 'disabled'))
);

ALTER TABLE channels
    ADD COLUMN bot_id UUID;

CREATE INDEX channels_bot_id_idx ON channels (bot_id) WHERE bot_id IS NOT NULL;

-- Seed a single local/dev Telegram bot. Empty token_enc (0x00) means
-- "use configured plaintext from env at runtime" for local/test.
INSERT INTO telegram_bots (id, username, token_enc, webhook_secret, status, created_at)
VALUES (
    '019a0000-0000-7000-8000-0000000000b1',
    'posts_local_bot',
    decode('00', 'hex'),
    'local-tg-webhook-secret',
    'active',
    now()
);
