-- BE-33: social remote moderation actions + own-post registry / publish intents (expand-only).

-- ---------------------------------------------------------------------------
-- social_remote_actions — partitioned by run_at (same pattern as publications)
-- ---------------------------------------------------------------------------
CREATE TABLE social_remote_actions (
    id                UUID NOT NULL,
    workspace_id      UUID NOT NULL,
    channel_id        UUID NOT NULL,
    kind              TEXT NOT NULL,
    target_ref        JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload           JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key   TEXT NOT NULL,
    state             TEXT NOT NULL DEFAULT 'pending',
    attempts          INT NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    locked_by         TEXT,
    locked_until      TIMESTAMPTZ,
    run_at            TIMESTAMPTZ NOT NULL,
    result            JSONB,
    error_code        TEXT,
    error_message     TEXT,
    source            TEXT NOT NULL,
    source_ref        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    PRIMARY KEY (id, run_at),
    CONSTRAINT social_remote_actions_kind_check CHECK (kind IN (
        'publish_suggested',
        'delete_post',
        'delete_comment',
        'restore_comment',
        'ban_user',
        'unban_user',
        'reply_comment',
        'notify_author'
    )),
    CONSTRAINT social_remote_actions_state_check CHECK (state IN (
        'pending',
        'queued',
        'in_progress',
        'succeeded',
        'failed_retryable',
        'failed_permanent',
        'cancelled',
        'unknown_outcome'
    )),
    CONSTRAINT social_remote_actions_source_check CHECK (source IN (
        'suggest',
        'antispam',
        'crosspost',
        'manual'
    )),
    CONSTRAINT social_remote_actions_idempotency_key_uq UNIQUE (idempotency_key, run_at)
) PARTITION BY RANGE (run_at);

COMMENT ON TABLE social_remote_actions IS 'BE-33 remote moderation / suggest action queue (SKIP LOCKED dispatcher)';

-- Dispatcher hot indexes (mirror publications)
CREATE INDEX social_remote_actions_due_idx
    ON social_remote_actions (state, run_at)
    WHERE state IN ('pending', 'failed_retryable');

CREATE INDEX social_remote_actions_due_coalesce_idx
    ON social_remote_actions ((COALESCE(next_attempt_at, run_at)))
    WHERE state IN ('pending', 'failed_retryable');

CREATE INDEX social_remote_actions_locked_until_idx
    ON social_remote_actions (locked_until)
    WHERE state IN ('queued', 'in_progress');

CREATE INDEX social_remote_actions_workspace_id_idx
    ON social_remote_actions (workspace_id);

CREATE INDEX social_remote_actions_channel_id_idx
    ON social_remote_actions (channel_id);

CREATE INDEX social_remote_actions_idempotency_key_idx
    ON social_remote_actions (idempotency_key);

-- Monthly partitions: 2026-10 .. 2027-09
CREATE TABLE social_remote_actions_2026_10 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2026-10-01+00') TO ('2026-11-01+00');
CREATE TABLE social_remote_actions_2026_11 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2026-11-01+00') TO ('2026-12-01+00');
CREATE TABLE social_remote_actions_2026_12 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2026-12-01+00') TO ('2027-01-01+00');
CREATE TABLE social_remote_actions_2027_01 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-01-01+00') TO ('2027-02-01+00');
CREATE TABLE social_remote_actions_2027_02 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-02-01+00') TO ('2027-03-01+00');
CREATE TABLE social_remote_actions_2027_03 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-03-01+00') TO ('2027-04-01+00');
CREATE TABLE social_remote_actions_2027_04 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-04-01+00') TO ('2027-05-01+00');
CREATE TABLE social_remote_actions_2027_05 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-05-01+00') TO ('2027-06-01+00');
CREATE TABLE social_remote_actions_2027_06 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-06-01+00') TO ('2027-07-01+00');
CREATE TABLE social_remote_actions_2027_07 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-07-01+00') TO ('2027-08-01+00');
CREATE TABLE social_remote_actions_2027_08 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-08-01+00') TO ('2027-09-01+00');
CREATE TABLE social_remote_actions_2027_09 PARTITION OF social_remote_actions
    FOR VALUES FROM ('2027-09-01+00') TO ('2027-10-01+00');

-- ---------------------------------------------------------------------------
-- remote_post_registry — own posts for loop protection / isOwn
-- ---------------------------------------------------------------------------
CREATE TABLE remote_post_registry (
    id                   UUID PRIMARY KEY,
    network              TEXT NOT NULL,
    channel_id           UUID NOT NULL REFERENCES channels (id),
    remote_post_id       TEXT NOT NULL,
    origin               TEXT NOT NULL,
    publication_id       UUID,
    post_id              UUID,
    root_origin          JSONB NOT NULL DEFAULT '{}'::jsonb,
    hop                  SMALLINT NOT NULL DEFAULT 0,
    content_fingerprint  TEXT,
    workspace_id         UUID NOT NULL REFERENCES workspaces (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT remote_post_registry_network_check
        CHECK (network IN ('vk', 'tg', 'ok', 'max', 'rutube')),
    CONSTRAINT remote_post_registry_origin_check
        CHECK (origin IN ('publication', 'suggest', 'crosspost')),
    CONSTRAINT remote_post_registry_channel_remote_uq UNIQUE (channel_id, remote_post_id)
);

COMMENT ON TABLE remote_post_registry IS 'BE-33 registry of posts we published (L1 own-post recognition)';

CREATE INDEX remote_post_registry_content_fingerprint_idx
    ON remote_post_registry (channel_id, content_fingerprint, created_at)
    WHERE content_fingerprint IS NOT NULL;

CREATE INDEX remote_post_registry_created_at_idx
    ON remote_post_registry (created_at);

CREATE INDEX remote_post_registry_workspace_id_idx
    ON remote_post_registry (workspace_id);

-- ---------------------------------------------------------------------------
-- publish_intents — pre-network race window (L2)
-- ---------------------------------------------------------------------------
CREATE TABLE publish_intents (
    channel_id           UUID NOT NULL REFERENCES channels (id),
    idempotency_key      TEXT NOT NULL,
    content_fingerprint  TEXT NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (channel_id, idempotency_key)
);

COMMENT ON TABLE publish_intents IS 'BE-33 short-lived publish intent so inbound events can match own posts before commit';

CREATE INDEX publish_intents_expires_at_idx
    ON publish_intents (expires_at);
