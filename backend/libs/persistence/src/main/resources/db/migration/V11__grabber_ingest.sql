-- BE-17: Grabber / RSS Ingest (E17)
-- automation_rules + grabber_runs + grabber_items
-- Expand-only; CHECKs via NOT VALID + VALIDATE (squawk-safe).

-- ---------------------------------------------------------------------------
-- automation_rules
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS automation_rules (
    id              UUID PRIMARY KEY,
    workspace_id    UUID NOT NULL REFERENCES workspaces (id),
    project_id      UUID NOT NULL REFERENCES projects (id),
    type            TEXT NOT NULL,
    name            TEXT NOT NULL,
    target_channel_id UUID NOT NULL REFERENCES channels (id),
    status          TEXT NOT NULL DEFAULT 'draft',
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_version  INT NOT NULL DEFAULT 1,
    user_responsibility_accepted_at TIMESTAMPTZ,
    user_responsibility_accepted_by UUID REFERENCES users (id),
    last_run_at     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    consecutive_failures INT NOT NULL DEFAULT 0,
    created_by      UUID NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INT NOT NULL DEFAULT 0
);

ALTER TABLE automation_rules
    ADD CONSTRAINT automation_rules_type_check
    CHECK (type IN ('grabber', 'rss', 'website')) NOT VALID;
ALTER TABLE automation_rules VALIDATE CONSTRAINT automation_rules_type_check;

ALTER TABLE automation_rules
    ADD CONSTRAINT automation_rules_status_check
    CHECK (status IN ('draft', 'active', 'paused', 'auto_paused', 'archived')) NOT VALID;
ALTER TABLE automation_rules VALIDATE CONSTRAINT automation_rules_status_check;

ALTER TABLE automation_rules
    ADD CONSTRAINT automation_rules_name_len_check
    CHECK (char_length(name) BETWEEN 1 AND 120) NOT VALID;
ALTER TABLE automation_rules VALIDATE CONSTRAINT automation_rules_name_len_check;

CREATE INDEX IF NOT EXISTS automation_rules_ws_type_status_idx
    ON automation_rules (workspace_id, type, status);

CREATE INDEX IF NOT EXISTS automation_rules_project_idx
    ON automation_rules (project_id, status);

CREATE INDEX IF NOT EXISTS automation_rules_due_idx
    ON automation_rules (status, next_run_at)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS automation_rules_channel_idx
    ON automation_rules (target_channel_id)
    WHERE status IN ('active', 'paused', 'auto_paused');

-- ---------------------------------------------------------------------------
-- grabber_runs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grabber_runs (
    id              UUID PRIMARY KEY,
    rule_id         UUID NOT NULL REFERENCES automation_rules (id),
    workspace_id    UUID NOT NULL REFERENCES workspaces (id),
    trigger         TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'running',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    sources_scanned INT NOT NULL DEFAULT 0,
    items_found     INT NOT NULL DEFAULT 0,
    items_new       INT NOT NULL DEFAULT 0,
    items_deduped   INT NOT NULL DEFAULT 0,
    items_published INT NOT NULL DEFAULT 0,
    error_code      TEXT,
    error_message   TEXT,
    trace_id        TEXT
);

ALTER TABLE grabber_runs
    ADD CONSTRAINT grabber_runs_trigger_check
    CHECK (trigger IN ('scheduled', 'manual', 'retry')) NOT VALID;
ALTER TABLE grabber_runs VALIDATE CONSTRAINT grabber_runs_trigger_check;

ALTER TABLE grabber_runs
    ADD CONSTRAINT grabber_runs_status_check
    CHECK (status IN ('running', 'succeeded', 'succeeded_partial', 'failed')) NOT VALID;
ALTER TABLE grabber_runs VALIDATE CONSTRAINT grabber_runs_status_check;

CREATE INDEX IF NOT EXISTS grabber_runs_rule_started_idx
    ON grabber_runs (rule_id, started_at DESC);

CREATE INDEX IF NOT EXISTS grabber_runs_ws_started_idx
    ON grabber_runs (workspace_id, started_at DESC);

-- ---------------------------------------------------------------------------
-- grabber_items
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grabber_items (
    id              UUID PRIMARY KEY,
    rule_id         UUID NOT NULL REFERENCES automation_rules (id),
    run_id          UUID REFERENCES grabber_runs (id),
    workspace_id    UUID NOT NULL REFERENCES workspaces (id),
    target_channel_id UUID NOT NULL REFERENCES channels (id),
    source_network  TEXT NOT NULL,
    source_id       TEXT NOT NULL,
    remote_post_id  TEXT NOT NULL,
    source_permalink TEXT,
    source_name     TEXT,
    content_text    TEXT,
    content_html    TEXT,
    attachments_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_hash    TEXT NOT NULL,
    media_hashes    TEXT[] NOT NULL DEFAULT '{}',
    status          TEXT NOT NULL,
    skip_reason     TEXT,
    post_id         UUID REFERENCES posts (id),
    publication_ids UUID[] NOT NULL DEFAULT '{}',
    moderated_by    UUID REFERENCES users (id),
    moderated_at    TIMESTAMPTZ,
    edited_text     TEXT,
    raw_payload     JSONB,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE grabber_items
    ADD CONSTRAINT grabber_items_status_check
    CHECK (status IN (
        'discovered',
        'filtered_out',
        'suppressed_dedup',
        'pending_moderation',
        'rejected',
        'approved',
        'scheduled',
        'published',
        'publish_failed',
        'failed_validation',
        'failed_ingest'
    )) NOT VALID;
ALTER TABLE grabber_items VALIDATE CONSTRAINT grabber_items_status_check;

ALTER TABLE grabber_items
    ADD CONSTRAINT grabber_items_content_hash_len_check
    CHECK (char_length(content_hash) = 64) NOT VALID;
ALTER TABLE grabber_items VALIDATE CONSTRAINT grabber_items_content_hash_len_check;

-- Idempotency on source remote key
CREATE UNIQUE INDEX IF NOT EXISTS uq_grabber_items_remote
    ON grabber_items (rule_id, source_network, source_id, remote_post_id);

-- Content-hash dedup within channel (denormalized target_channel_id)
CREATE UNIQUE INDEX IF NOT EXISTS uq_grabber_items_hash_channel
    ON grabber_items (workspace_id, target_channel_id, content_hash)
    WHERE status NOT IN ('rejected', 'suppressed_dedup', 'filtered_out');

CREATE INDEX IF NOT EXISTS grabber_items_rule_detected_idx
    ON grabber_items (rule_id, detected_at DESC);

CREATE INDEX IF NOT EXISTS grabber_items_ws_status_idx
    ON grabber_items (workspace_id, status, detected_at DESC);

CREATE INDEX IF NOT EXISTS grabber_items_moderation_queue_idx
    ON grabber_items (workspace_id, status, detected_at)
    WHERE status = 'pending_moderation';
