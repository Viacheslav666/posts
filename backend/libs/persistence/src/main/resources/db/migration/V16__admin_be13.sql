-- BE-13: platform admin API foundation (expand-only, Squawk-safe).
-- Spec: docs/specs/01-analyst.md E7, docs/specs/03-frontend.md A13

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- users: platform_admin flag (not a workspace role)
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS platform_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS users_platform_admin_idx
    ON users (platform_admin)
    WHERE platform_admin = TRUE;

CREATE INDEX IF NOT EXISTS users_email_trgm_idx
    ON users USING gin ((email::text) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS users_name_trgm_idx
    ON users USING gin (name gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- admin_audit_log: immutable platform-admin actions (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE admin_audit_log (
    id              UUID PRIMARY KEY,
    actor_user_id   UUID NOT NULL REFERENCES users (id),
    action          TEXT NOT NULL,
    target_type     TEXT NOT NULL,
    target_id       UUID,
    reason          TEXT,
    details         JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip              INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX admin_audit_log_actor_created_idx
    ON admin_audit_log (actor_user_id, created_at DESC);

CREATE INDEX admin_audit_log_action_created_idx
    ON admin_audit_log (action, created_at DESC);

CREATE INDEX admin_audit_log_target_idx
    ON admin_audit_log (target_type, target_id);

CREATE OR REPLACE FUNCTION admin_audit_log_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'admin_audit_log is append-only';
END;
$$;

CREATE TRIGGER admin_audit_log_no_update
    BEFORE UPDATE ON admin_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION admin_audit_log_immutable();

CREATE TRIGGER admin_audit_log_no_delete
    BEFORE DELETE ON admin_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION admin_audit_log_immutable();

-- ---------------------------------------------------------------------------
-- feature_flags (global) + workspace overrides
-- ---------------------------------------------------------------------------
CREATE TABLE feature_flags (
    id               UUID PRIMARY KEY,
    key              TEXT NOT NULL,
    description      TEXT,
    enabled_global   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT feature_flags_key_uq UNIQUE (key)
);

ALTER TABLE feature_flags
    ADD CONSTRAINT feature_flags_key_format_check
    CHECK (key ~ '^[a-z][a-z0-9_.-]{1,63}$') NOT VALID;
ALTER TABLE feature_flags VALIDATE CONSTRAINT feature_flags_key_format_check;

CREATE TABLE workspace_feature_flags (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    flag_key         TEXT NOT NULL,
    enabled          BOOLEAN NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT workspace_feature_flags_ws_key_uq UNIQUE (workspace_id, flag_key)
);

ALTER TABLE workspace_feature_flags
    ADD CONSTRAINT workspace_feature_flags_flag_key_fkey
    FOREIGN KEY (flag_key) REFERENCES feature_flags (key) NOT VALID;
ALTER TABLE workspace_feature_flags VALIDATE CONSTRAINT workspace_feature_flags_flag_key_fkey;

CREATE INDEX workspace_feature_flags_flag_key_idx
    ON workspace_feature_flags (flag_key);

-- ---------------------------------------------------------------------------
-- announcements (user-facing banners)
-- ---------------------------------------------------------------------------
CREATE TABLE announcements (
    id               UUID PRIMARY KEY,
    title            TEXT NOT NULL,
    body             TEXT NOT NULL,
    severity         TEXT NOT NULL DEFAULT 'info',
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at          TIMESTAMPTZ,
    created_by       UUID REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE announcements
    ADD CONSTRAINT announcements_severity_check
    CHECK (severity IN ('info', 'warning', 'critical')) NOT VALID;
ALTER TABLE announcements VALIDATE CONSTRAINT announcements_severity_check;

CREATE INDEX announcements_active_window_idx
    ON announcements (active, starts_at, ends_at);

-- ---------------------------------------------------------------------------
-- admin_idempotency: DLQ retry/delete without duplicate publications
-- ---------------------------------------------------------------------------
CREATE TABLE admin_idempotency (
    id               UUID PRIMARY KEY,
    actor_user_id    UUID NOT NULL REFERENCES users (id),
    scope            TEXT NOT NULL,
    idempotency_key  TEXT NOT NULL,
    request_hash     TEXT NOT NULL,
    response_body    JSONB,
    status_code      INT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT admin_idempotency_scope_key_uq UNIQUE (scope, idempotency_key)
);

CREATE INDEX admin_idempotency_created_idx ON admin_idempotency (created_at);

-- ---------------------------------------------------------------------------
-- workspaces search helpers
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS workspaces_name_trgm_idx
    ON workspaces USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS workspaces_billing_status_idx
    ON workspaces (billing_status)
    WHERE deleted_at IS NULL;
