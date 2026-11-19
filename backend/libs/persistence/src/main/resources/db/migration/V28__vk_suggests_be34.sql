-- BE-34: VK suggested posts automation (expand-only, Squawk-safe).

-- ---------------------------------------------------------------------------
-- suggest_settings — one config row per VK channel
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suggest_settings (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspaces (id),
    project_id      UUID NOT NULL REFERENCES projects (id),
    channel_id      UUID NOT NULL REFERENCES channels (id),
    status          TEXT NOT NULL DEFAULT 'draft',
    mode            TEXT NOT NULL DEFAULT 'auto',
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_version  INT NOT NULL DEFAULT 1,
    created_by      UUID NOT NULL REFERENCES users (id),
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_sync_at    TIMESTAMPTZ,
    next_sync_at    TIMESTAMPTZ
);

ALTER TABLE suggest_settings
    ADD CONSTRAINT suggest_settings_status_check
    CHECK (status IN ('draft', 'active', 'paused', 'auto_paused')) NOT VALID;
ALTER TABLE suggest_settings VALIDATE CONSTRAINT suggest_settings_status_check;

ALTER TABLE suggest_settings
    ADD CONSTRAINT suggest_settings_mode_check
    CHECK (mode IN ('auto', 'manual', 'hybrid')) NOT VALID;
ALTER TABLE suggest_settings VALIDATE CONSTRAINT suggest_settings_mode_check;

CREATE UNIQUE INDEX IF NOT EXISTS uq_suggest_settings_channel
    ON suggest_settings (channel_id);

CREATE INDEX IF NOT EXISTS suggest_settings_ws_status_idx
    ON suggest_settings (workspace_id, status);

CREATE INDEX IF NOT EXISTS suggest_settings_project_idx
    ON suggest_settings (project_id, status);

CREATE INDEX IF NOT EXISTS suggest_settings_sync_due_idx
    ON suggest_settings (status, next_sync_at)
    WHERE status = 'active';

COMMENT ON TABLE suggest_settings IS 'BE-34 VK suggest autopost settings (one per channel)';

-- ---------------------------------------------------------------------------
-- suggest_items — suggested posts queue / moderation
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suggest_items (
    id                        UUID PRIMARY KEY,
    workspace_id              UUID NOT NULL REFERENCES workspaces (id),
    channel_id                UUID NOT NULL REFERENCES channels (id),
    settings_id               UUID NOT NULL REFERENCES suggest_settings (id),
    vk_post_id                TEXT NOT NULL,
    author_vk_id              TEXT,
    text                      TEXT,
    attachments               JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_fingerprint       TEXT,
    simhash                   BIGINT,
    status                    TEXT NOT NULL,
    reasons                   TEXT[] NOT NULL DEFAULT '{}',
    decided_by                UUID REFERENCES users (id),
    decided_at                TIMESTAMPTZ,
    scheduled_for             TIMESTAMPTZ,
    remote_action_id          UUID,
    published_remote_post_id  TEXT,
    raw                       JSONB,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE suggest_items
    ADD CONSTRAINT suggest_items_status_check
    CHECK (status IN (
        'new',
        'filtered_out',
        'pending_moderation',
        'approved',
        'scheduled',
        'publishing',
        'published',
        'rejected',
        'deleted_remote',
        'removed_externally',
        'failed'
    )) NOT VALID;
ALTER TABLE suggest_items VALIDATE CONSTRAINT suggest_items_status_check;

CREATE UNIQUE INDEX IF NOT EXISTS uq_suggest_items_channel_vk_post
    ON suggest_items (channel_id, vk_post_id);

CREATE INDEX IF NOT EXISTS suggest_items_channel_status_created_idx
    ON suggest_items (channel_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS suggest_items_ws_status_idx
    ON suggest_items (workspace_id, status);

CREATE INDEX IF NOT EXISTS suggest_items_author_idx
    ON suggest_items (channel_id, author_vk_id, created_at DESC);

CREATE INDEX IF NOT EXISTS suggest_items_scheduled_idx
    ON suggest_items (status, scheduled_for)
    WHERE status = 'scheduled';

COMMENT ON TABLE suggest_items IS 'BE-34 VK suggested posts moderation / publish queue';

-- ---------------------------------------------------------------------------
-- suggest_bulk_jobs — async bulk approve/reject progress (BE-34)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suggest_bulk_jobs (
    id                UUID PRIMARY KEY,
    workspace_id      UUID NOT NULL REFERENCES workspaces (id),
    settings_id       UUID NOT NULL REFERENCES suggest_settings (id),
    channel_id        UUID NOT NULL REFERENCES channels (id),
    action            TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'pending',
    idempotency_key   TEXT NOT NULL,
    total             INT NOT NULL DEFAULT 0,
    processed         INT NOT NULL DEFAULT 0,
    succeeded         INT NOT NULL DEFAULT 0,
    failed            INT NOT NULL DEFAULT 0,
    item_ids          UUID[] NOT NULL DEFAULT '{}',
    created_by        UUID NOT NULL REFERENCES users (id),
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ
);

ALTER TABLE suggest_bulk_jobs
    ADD CONSTRAINT suggest_bulk_jobs_action_check
    CHECK (action IN ('approve_all', 'reject_all', 'approve_ids', 'reject_ids')) NOT VALID;
ALTER TABLE suggest_bulk_jobs VALIDATE CONSTRAINT suggest_bulk_jobs_action_check;

ALTER TABLE suggest_bulk_jobs
    ADD CONSTRAINT suggest_bulk_jobs_status_check
    CHECK (status IN ('pending', 'running', 'succeeded', 'failed', 'cancelled')) NOT VALID;
ALTER TABLE suggest_bulk_jobs VALIDATE CONSTRAINT suggest_bulk_jobs_status_check;

CREATE UNIQUE INDEX IF NOT EXISTS uq_suggest_bulk_jobs_idempotency
    ON suggest_bulk_jobs (workspace_id, idempotency_key);

CREATE INDEX IF NOT EXISTS suggest_bulk_jobs_settings_idx
    ON suggest_bulk_jobs (settings_id, created_at DESC);

COMMENT ON TABLE suggest_bulk_jobs IS 'BE-34 async bulk moderation jobs with progress';

-- Feature flag for module gating (no dedicated plan-limit key yet; TODO tariff limit).
INSERT INTO feature_flags (id, key, description, enabled_global, created_at, updated_at)
VALUES (
    '018f0000-0000-7000-8000-000000000034'::uuid,
    'automation.suggest',
    'BE-34 VK suggest autopost module',
    TRUE,
    now(),
    now()
)
ON CONFLICT (key) DO NOTHING;
