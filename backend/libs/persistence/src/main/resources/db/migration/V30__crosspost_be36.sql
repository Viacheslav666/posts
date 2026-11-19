-- BE-36: crosspost rules / targets / items (expand-only).

-- ---------------------------------------------------------------------------
-- crosspost_rules
-- ---------------------------------------------------------------------------
CREATE TABLE crosspost_rules (
    id                 UUID PRIMARY KEY,
    workspace_id       UUID NOT NULL REFERENCES workspaces (id),
    project_id         UUID NOT NULL REFERENCES projects (id),
    source_channel_id  UUID NOT NULL REFERENCES channels (id),
    name               TEXT NOT NULL,
    status             TEXT NOT NULL DEFAULT 'draft',
    config             JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_version     INT NOT NULL DEFAULT 1,
    created_by         UUID NOT NULL,
    version            INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crosspost_rules_status_check CHECK (status IN (
        'draft', 'active', 'paused', 'auto_paused'
    ))
);

COMMENT ON TABLE crosspost_rules IS 'BE-36 crosspost automation rules (source channel + filters/transforms)';

CREATE INDEX crosspost_rules_workspace_id_idx ON crosspost_rules (workspace_id);
CREATE INDEX crosspost_rules_project_id_idx ON crosspost_rules (project_id);
CREATE INDEX crosspost_rules_source_channel_id_idx ON crosspost_rules (source_channel_id);
CREATE INDEX crosspost_rules_workspace_status_idx ON crosspost_rules (workspace_id, status);

-- ---------------------------------------------------------------------------
-- crosspost_rule_targets
-- ---------------------------------------------------------------------------
CREATE TABLE crosspost_rule_targets (
    id                 UUID PRIMARY KEY,
    rule_id            UUID NOT NULL REFERENCES crosspost_rules (id) ON DELETE CASCADE,
    target_channel_id  UUID NOT NULL REFERENCES channels (id),
    options            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crosspost_rule_targets_rule_target_uq UNIQUE (rule_id, target_channel_id)
);

COMMENT ON TABLE crosspost_rule_targets IS 'BE-36 per-rule destination channels (source≠target enforced in service)';

CREATE INDEX crosspost_rule_targets_target_channel_id_idx
    ON crosspost_rule_targets (target_channel_id);

-- ---------------------------------------------------------------------------
-- crosspost_items — journal + deferred processing queue
-- ---------------------------------------------------------------------------
CREATE TABLE crosspost_items (
    id                   UUID PRIMARY KEY,
    workspace_id         UUID NOT NULL REFERENCES workspaces (id),
    rule_id              UUID NOT NULL REFERENCES crosspost_rules (id),
    source_channel_id    UUID NOT NULL REFERENCES channels (id),
    source_remote_id     TEXT NOT NULL,
    source_network       TEXT NOT NULL,
    status               TEXT NOT NULL DEFAULT 'received',
    reasons              TEXT[] NOT NULL DEFAULT '{}'::text[],
    post_id              UUID,
    publication_ids      UUID[] NOT NULL DEFAULT '{}'::uuid[],
    content_text         TEXT,
    attachments_json     JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_fingerprint  TEXT,
    simhash              BIGINT,
    media_hashes         TEXT[] NOT NULL DEFAULT '{}'::text[],
    payload              JSONB NOT NULL DEFAULT '{}'::jsonb,
    process_after        TIMESTAMPTZ NOT NULL DEFAULT now(),
    consecutive_loop_skips INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crosspost_items_rule_remote_uq UNIQUE (rule_id, source_remote_id),
    CONSTRAINT crosspost_items_status_check CHECK (status IN (
        'received',
        'filtered_out',
        'skipped_own',
        'skipped_loop',
        'skipped_duplicate',
        'skipped_unsupported_media',
        'queued',
        'published',
        'partially_published',
        'failed'
    )),
    CONSTRAINT crosspost_items_network_check CHECK (source_network IN ('vk', 'tg', 'max', 'ok'))
);

COMMENT ON TABLE crosspost_items IS 'BE-36 crosspost journal; process_after supports L2 15s deferred handling';

CREATE INDEX crosspost_items_workspace_id_idx ON crosspost_items (workspace_id);
CREATE INDEX crosspost_items_rule_id_idx ON crosspost_items (rule_id);
CREATE INDEX crosspost_items_status_idx ON crosspost_items (status);
CREATE INDEX crosspost_items_due_idx
    ON crosspost_items (process_after)
    WHERE status = 'received';
CREATE INDEX crosspost_items_created_at_idx ON crosspost_items (rule_id, created_at DESC);
CREATE INDEX crosspost_items_fingerprint_idx
    ON crosspost_items (workspace_id, content_fingerprint, created_at)
    WHERE content_fingerprint IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Feature flag + plan limits (automation.crosspost / crosspost_rules)
-- ---------------------------------------------------------------------------
INSERT INTO feature_flags (id, key, description, enabled_global, created_at, updated_at)
VALUES (
    '019a0000-be36-7000-8000-000000000001'::uuid,
    'automation.crosspost',
    'BE-36 crosspost automations (VK/TG/Max → VK/OK/TG/Max)',
    true,
    now(),
    now()
)
ON CONFLICT (key) DO NOTHING;

UPDATE plans
SET limits = limits || '{"crosspost_rules": 0}'::jsonb
WHERE code = 'free';

UPDATE plans
SET limits = limits || jsonb_build_object(
    'crosspost_rules',
    GREATEST(COALESCE((limits ->> 'grabber_rules')::int, 1), 1)
)
WHERE code <> 'free'
  AND (limits ->> 'crosspost_rules') IS NULL;
