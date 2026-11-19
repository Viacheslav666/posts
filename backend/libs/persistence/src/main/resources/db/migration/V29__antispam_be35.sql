-- BE-35: antispam rules, workspace word lists, action log (expand-only).
-- V28 reserved for BE-34 (suggest); V30 for BE-36 (crosspost). V22 skipped.

-- ---------------------------------------------------------------------------
-- feature flag (tariff / module gate, same pattern as BE-34 automation.suggest)
-- ---------------------------------------------------------------------------
INSERT INTO feature_flags (id, key, description, enabled_global, created_at, updated_at)
VALUES (
    '01940000-0000-7000-8000-000000000035',
    'automation.antispam',
    'BE-35 antispam module (VK / Telegram / Max)',
    true,
    now(),
    now()
)
ON CONFLICT (key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- antispam_rules — multiple configs per channel; first match by priority wins
-- ---------------------------------------------------------------------------
CREATE TABLE antispam_rules (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspaces (id),
    project_id      UUID NOT NULL REFERENCES projects (id),
    channel_id      UUID NOT NULL REFERENCES channels (id),
    name            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'draft',
    priority        INT NOT NULL DEFAULT 100,
    sections        TEXT[] NOT NULL DEFAULT '{}',
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_version  INT NOT NULL DEFAULT 1,
    dry_run         BOOLEAN NOT NULL DEFAULT false,
    created_by      UUID NOT NULL,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE antispam_rules
    ADD CONSTRAINT antispam_rules_status_check
    CHECK (status IN ('draft', 'active', 'paused', 'archived')) NOT VALID;
ALTER TABLE antispam_rules VALIDATE CONSTRAINT antispam_rules_status_check;

CREATE INDEX antispam_rules_workspace_project_idx
    ON antispam_rules (workspace_id, project_id);

CREATE INDEX antispam_rules_channel_status_priority_idx
    ON antispam_rules (channel_id, status, priority);

CREATE INDEX antispam_rules_workspace_status_idx
    ON antispam_rules (workspace_id, status);

COMMENT ON TABLE antispam_rules IS 'BE-35 antispam filter configs; multiple per channel, matched by priority';

-- ---------------------------------------------------------------------------
-- antispam_word_lists — shared workspace stop-word / whitelist dictionaries
-- ---------------------------------------------------------------------------
CREATE TABLE antispam_word_lists (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspaces (id),
    name            TEXT NOT NULL,
    words           TEXT[] NOT NULL DEFAULT '{}',
    mode            TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE antispam_word_lists
    ADD CONSTRAINT antispam_word_lists_mode_check
    CHECK (mode IN ('black', 'white')) NOT VALID;
ALTER TABLE antispam_word_lists VALIDATE CONSTRAINT antispam_word_lists_mode_check;

CREATE INDEX antispam_word_lists_workspace_idx
    ON antispam_word_lists (workspace_id);

COMMENT ON TABLE antispam_word_lists IS 'BE-35 workspace-scoped keyword dictionaries for antispam rules';

-- ---------------------------------------------------------------------------
-- antispam_log — monthly partitions by created_at, retention 90d
-- PK/UNIQUE must include partition key (PostgreSQL). App-level dedup looks up
-- (channel_id, section, remote_comment_key) across partitions.
-- ---------------------------------------------------------------------------
CREATE TABLE antispam_log (
    id                      UUID NOT NULL,
    workspace_id            UUID NOT NULL,
    channel_id              UUID NOT NULL,
    rule_id                 UUID,
    section                 TEXT NOT NULL,
    remote_comment_key      TEXT NOT NULL,
    remote_ref              JSONB NOT NULL DEFAULT '{}'::jsonb,
    author_ref              TEXT,
    text_snapshot           TEXT,
    attachments_summary     TEXT,
    reasons                 TEXT[] NOT NULL DEFAULT '{}',
    action                  TEXT NOT NULL,
    state                   TEXT NOT NULL DEFAULT 'pending',
    restorable              BOOLEAN NOT NULL DEFAULT false,
    remote_action_ids       UUID[] NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL,
    restored_by             UUID,
    restored_at             TIMESTAMPTZ,
    PRIMARY KEY (id, created_at),
    CONSTRAINT antispam_log_dedup_uq UNIQUE (channel_id, section, remote_comment_key, created_at)
) PARTITION BY RANGE (created_at);

ALTER TABLE antispam_log
    ADD CONSTRAINT antispam_log_action_check
    CHECK (action IN ('flagged', 'deleted', 'deleted_banned', 'replied')) NOT VALID;
ALTER TABLE antispam_log VALIDATE CONSTRAINT antispam_log_action_check;

ALTER TABLE antispam_log
    ADD CONSTRAINT antispam_log_state_check
    CHECK (state IN ('pending', 'done', 'failed', 'restored', 'restore_failed')) NOT VALID;
ALTER TABLE antispam_log VALIDATE CONSTRAINT antispam_log_state_check;

CREATE INDEX antispam_log_workspace_created_idx
    ON antispam_log (workspace_id, created_at DESC);

CREATE INDEX antispam_log_channel_created_idx
    ON antispam_log (channel_id, created_at DESC);

CREATE INDEX antispam_log_rule_created_idx
    ON antispam_log (rule_id, created_at DESC);

CREATE INDEX antispam_log_dedup_lookup_idx
    ON antispam_log (channel_id, section, remote_comment_key);

COMMENT ON TABLE antispam_log IS 'BE-35 antispam action history; monthly partitions, 90d retention';

-- Seed monthly partitions: current month-1 .. current+2 (UTC).
-- Further months are created by AntispamPartitionJob (ShedLock).
DO $$
DECLARE
    m date;
    part_name text;
    from_ts text;
    to_ts text;
BEGIN
    FOR m IN
        SELECT date_trunc('month', d)::date
        FROM generate_series(
            (date_trunc('month', CURRENT_DATE) - interval '1 month')::date,
            (date_trunc('month', CURRENT_DATE) + interval '2 month')::date,
            interval '1 month'
        ) AS d
    LOOP
        part_name := 'antispam_log_' || to_char(m, 'YYYY_MM');
        from_ts := to_char(m, 'YYYY-MM-DD') || '+00';
        to_ts := to_char((m + interval '1 month')::date, 'YYYY-MM-DD') || '+00';
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF antispam_log FOR VALUES FROM (%L) TO (%L)',
            part_name,
            from_ts,
            to_ts
        );
    END LOOP;
END $$;
