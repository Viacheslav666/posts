-- BE-2: core domain schema (02-backend.md §4). Expand-only; greenfield CREATE TABLE.
-- PK = UUIDv7 (app-generated). All timestamps timestamptz UTC. Soft delete via deleted_at where noted.

CREATE EXTENSION IF NOT EXISTS citext;

-- ---------------------------------------------------------------------------
-- Billing catalogue (referenced by workspaces)
-- ---------------------------------------------------------------------------
CREATE TABLE plans (
    id           UUID PRIMARY KEY,
    code         TEXT NOT NULL,
    name         TEXT NOT NULL,
    price_month  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_year   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    limits       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT plans_code_uq UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- Users and access
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    email              CITEXT NOT NULL,
    email_verified_at  TIMESTAMPTZ,
    password_hash      TEXT,
    name               TEXT NOT NULL,
    avatar_url         TEXT,
    timezone           TEXT NOT NULL DEFAULT 'Europe/Moscow',
    locale             TEXT NOT NULL DEFAULT 'ru',
    totp_secret_enc    BYTEA,
    totp_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT users_email_uq UNIQUE (email)
);

CREATE TABLE user_identities (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users (id),
    provider          TEXT NOT NULL,
    provider_user_id  TEXT NOT NULL,
    profile           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_identities_provider_check
        CHECK (provider IN ('vk', 'yandex', 'telegram')),
    CONSTRAINT user_identities_provider_user_uq UNIQUE (provider, provider_user_id)
);

CREATE INDEX user_identities_user_id_idx ON user_identities (user_id);

CREATE TABLE sessions (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users (id),
    refresh_token_hash  TEXT NOT NULL,
    user_agent          TEXT,
    ip                  INET,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ
);

CREATE INDEX sessions_user_id_idx ON sessions (user_id);
CREATE INDEX sessions_refresh_token_hash_idx ON sessions (refresh_token_hash)
    WHERE revoked_at IS NULL;

-- ---------------------------------------------------------------------------
-- Workspaces / tenancy
-- ---------------------------------------------------------------------------
CREATE TABLE workspaces (
    id                  UUID PRIMARY KEY,
    name                TEXT NOT NULL,
    owner_user_id       UUID NOT NULL REFERENCES users (id),
    plan_id             UUID REFERENCES plans (id),
    billing_status      TEXT NOT NULL DEFAULT 'trial',
    trial_ends_at       TIMESTAMPTZ,
    current_period_end  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT workspaces_billing_status_check
        CHECK (billing_status IN ('trial', 'active', 'grace', 'frozen'))
);

CREATE INDEX workspaces_owner_user_id_idx ON workspaces (owner_user_id);
CREATE INDEX workspaces_plan_id_idx ON workspaces (plan_id);

CREATE TABLE workspace_members (
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    role          TEXT NOT NULL,
    invited_by    UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id),
    CONSTRAINT workspace_members_role_check
        CHECK (role IN ('owner', 'admin', 'editor', 'author', 'viewer'))
);

CREATE INDEX workspace_members_user_id_idx ON workspace_members (user_id);

CREATE TABLE invitations (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    email        CITEXT NOT NULL,
    role         TEXT NOT NULL,
    project_ids  UUID[] NOT NULL DEFAULT '{}',
    token_hash   TEXT NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    accepted_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    invited_by   UUID REFERENCES users (id),
    CONSTRAINT invitations_role_check
        CHECK (role IN ('admin', 'editor', 'author', 'viewer')),
    CONSTRAINT invitations_token_hash_uq UNIQUE (token_hash)
);

CREATE INDEX invitations_workspace_id_idx ON invitations (workspace_id);
CREATE INDEX invitations_email_idx ON invitations (email);

CREATE TABLE projects (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    name         TEXT NOT NULL,
    color        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX projects_workspace_id_idx ON projects (workspace_id);

CREATE TABLE project_members (
    project_id  UUID NOT NULL REFERENCES projects (id),
    user_id     UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX project_members_user_id_idx ON project_members (user_id);

-- ---------------------------------------------------------------------------
-- Channels
-- ---------------------------------------------------------------------------
CREATE TABLE social_accounts (
    id                 UUID PRIMARY KEY,
    workspace_id       UUID NOT NULL REFERENCES workspaces (id),
    network            TEXT NOT NULL,
    external_user_id   TEXT NOT NULL,
    display_name       TEXT,
    access_token_enc   BYTEA,
    refresh_token_enc  BYTEA,
    token_expires_at   TIMESTAMPTZ,
    scopes             TEXT[] NOT NULL DEFAULT '{}',
    status             TEXT NOT NULL DEFAULT 'active',
    last_checked_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT social_accounts_network_check
        CHECK (network IN ('vk', 'tg', 'ok', 'max', 'rutube')),
    CONSTRAINT social_accounts_status_check
        CHECK (status IN ('active', 'reauth_required', 'revoked'))
);

CREATE INDEX social_accounts_workspace_id_idx ON social_accounts (workspace_id);
CREATE UNIQUE INDEX social_accounts_workspace_network_external_uq
    ON social_accounts (workspace_id, network, external_user_id);

CREATE TABLE channels (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL REFERENCES workspaces (id),
    project_id          UUID REFERENCES projects (id),
    social_account_id   UUID REFERENCES social_accounts (id),
    network             TEXT NOT NULL,
    external_id         TEXT NOT NULL,
    type                TEXT NOT NULL,
    title               TEXT NOT NULL,
    avatar_url          TEXT,
    username            TEXT,
    group_token_enc     BYTEA,
    capabilities        JSONB NOT NULL DEFAULT '{}'::jsonb,
    status              TEXT NOT NULL DEFAULT 'active',
    status_reason       TEXT,
    timezone            TEXT NOT NULL DEFAULT 'Europe/Moscow',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT channels_network_check
        CHECK (network IN ('vk', 'tg', 'ok', 'max', 'rutube')),
    CONSTRAINT channels_type_check
        CHECK (type IN ('group', 'page', 'channel', 'chat', 'profile')),
    CONSTRAINT channels_status_check
        CHECK (status IN ('active', 'reauth_required', 'no_permissions', 'disabled')),
    CONSTRAINT channels_workspace_network_external_uq UNIQUE (workspace_id, network, external_id)
);

CREATE INDEX channels_workspace_id_idx ON channels (workspace_id);
CREATE INDEX channels_project_id_idx ON channels (project_id);
CREATE INDEX channels_social_account_id_idx ON channels (social_account_id);

CREATE TABLE channel_sets (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    name         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX channel_sets_workspace_id_idx ON channel_sets (workspace_id);

CREATE TABLE channel_set_items (
    set_id      UUID NOT NULL REFERENCES channel_sets (id) ON DELETE CASCADE,
    channel_id  UUID NOT NULL REFERENCES channels (id),
    PRIMARY KEY (set_id, channel_id)
);

CREATE INDEX channel_set_items_channel_id_idx ON channel_set_items (channel_id);

-- ---------------------------------------------------------------------------
-- Media & posts
-- ---------------------------------------------------------------------------
CREATE TABLE media_assets (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    uploader_id   UUID REFERENCES users (id),
    kind          TEXT NOT NULL,
    s3_key        TEXT NOT NULL,
    mime          TEXT,
    size_bytes    BIGINT,
    width         INT,
    height        INT,
    duration_ms   INT,
    sha256        TEXT,
    status        TEXT NOT NULL DEFAULT 'uploading',
    derivatives   JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT media_assets_kind_check
        CHECK (kind IN ('image', 'video', 'doc', 'audio')),
    CONSTRAINT media_assets_status_check
        CHECK (status IN ('uploading', 'processing', 'ready', 'failed'))
);

CREATE INDEX media_assets_workspace_id_idx ON media_assets (workspace_id);
CREATE INDEX media_assets_sha256_idx ON media_assets (workspace_id, sha256)
    WHERE sha256 IS NOT NULL;

CREATE TABLE posts (
    id             UUID PRIMARY KEY,
    workspace_id   UUID NOT NULL REFERENCES workspaces (id),
    project_id     UUID REFERENCES projects (id),
    author_id      UUID REFERENCES users (id),
    title          TEXT,
    base_content   JSONB NOT NULL DEFAULT '{}'::jsonb,
    status         TEXT NOT NULL DEFAULT 'draft',
    scheduled_at   TIMESTAMPTZ,
    recurrence     JSONB,
    rubric_id      UUID,
    is_ad          BOOLEAN NOT NULL DEFAULT FALSE,
    ord_creative_id UUID,
    version        INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT posts_status_check CHECK (status IN (
        'draft', 'pending_approval', 'approved', 'scheduled', 'publishing',
        'published', 'partially_published', 'failed', 'paused', 'archived'
    ))
);

CREATE INDEX posts_workspace_id_idx ON posts (workspace_id);
CREATE INDEX posts_project_id_idx ON posts (project_id);
CREATE INDEX posts_scheduled_at_idx ON posts (workspace_id, scheduled_at)
    WHERE deleted_at IS NULL;

CREATE TABLE post_variants (
    id          UUID PRIMARY KEY,
    post_id     UUID NOT NULL REFERENCES posts (id),
    channel_id  UUID NOT NULL REFERENCES channels (id),
    network     TEXT NOT NULL,
    content     JSONB NOT NULL DEFAULT '{}'::jsonb,
    options     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT post_variants_network_check
        CHECK (network IN ('vk', 'tg', 'ok', 'max', 'rutube')),
    CONSTRAINT post_variants_post_channel_uq UNIQUE (post_id, channel_id)
);

CREATE INDEX post_variants_channel_id_idx ON post_variants (channel_id);

-- ---------------------------------------------------------------------------
-- Publications (range-partitioned by run_at) — hot path for scheduler SKIP LOCKED
-- PK/UNIQUE must include partition key (PostgreSQL rule).
-- ---------------------------------------------------------------------------
CREATE TABLE publications (
    id                UUID NOT NULL,
    workspace_id      UUID NOT NULL,
    post_id           UUID NOT NULL,
    variant_id        UUID NOT NULL,
    channel_id        UUID NOT NULL,
    network           TEXT NOT NULL,
    run_at            TIMESTAMPTZ NOT NULL,
    idempotency_key   TEXT NOT NULL,
    state             TEXT NOT NULL DEFAULT 'pending',
    attempts          INT NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    locked_by         TEXT,
    locked_until      TIMESTAMPTZ,
    remote_post_id    TEXT,
    remote_url        TEXT,
    error_code        TEXT,
    error_message     TEXT,
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, run_at),
    CONSTRAINT publications_network_check
        CHECK (network IN ('vk', 'tg', 'ok', 'max', 'rutube')),
    CONSTRAINT publications_state_check CHECK (state IN (
        'pending', 'queued', 'in_progress', 'succeeded',
        'failed_retryable', 'failed_permanent', 'cancelled'
    )),
    CONSTRAINT publications_idempotency_key_uq UNIQUE (idempotency_key, run_at)
) PARTITION BY RANGE (run_at);

-- Dispatcher hot index (02-backend.md §4 / ADR-0004)
CREATE INDEX publications_due_idx
    ON publications (state, run_at)
    WHERE state IN ('pending', 'failed_retryable');

-- Expression index matching coalesce(next_attempt_at, run_at) in dispatcher SQL
CREATE INDEX publications_due_coalesce_idx
    ON publications ((COALESCE(next_attempt_at, run_at)))
    WHERE state IN ('pending', 'failed_retryable');

CREATE INDEX publications_post_id_idx ON publications (post_id);
CREATE INDEX publications_channel_id_run_at_idx ON publications (channel_id, run_at);
CREATE INDEX publications_workspace_id_idx ON publications (workspace_id);
CREATE INDEX publications_locked_until_idx
    ON publications (locked_until)
    WHERE state IN ('queued', 'in_progress');

-- Monthly partitions: 2026-10 .. 2027-03 (scheduler extends further via ShedLock)
CREATE TABLE publications_2026_10 PARTITION OF publications
    FOR VALUES FROM ('2026-10-01+00') TO ('2026-11-01+00');
CREATE TABLE publications_2026_11 PARTITION OF publications
    FOR VALUES FROM ('2026-11-01+00') TO ('2026-12-01+00');
CREATE TABLE publications_2026_12 PARTITION OF publications
    FOR VALUES FROM ('2026-12-01+00') TO ('2027-01-01+00');
CREATE TABLE publications_2027_01 PARTITION OF publications
    FOR VALUES FROM ('2027-01-01+00') TO ('2027-02-01+00');
CREATE TABLE publications_2027_02 PARTITION OF publications
    FOR VALUES FROM ('2027-02-01+00') TO ('2027-03-01+00');
CREATE TABLE publications_2027_03 PARTITION OF publications
    FOR VALUES FROM ('2027-03-01+00') TO ('2027-04-01+00');

CREATE TABLE publication_attempts (
    id                   UUID PRIMARY KEY,
    publication_id       UUID NOT NULL,
    publication_run_at   TIMESTAMPTZ NOT NULL,
    attempt_no           INT NOT NULL,
    started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ,
    http_status          INT,
    error_code           TEXT,
    response_excerpt     JSONB,
    CONSTRAINT publication_attempts_publication_fk
        FOREIGN KEY (publication_id, publication_run_at)
        REFERENCES publications (id, run_at),
    CONSTRAINT publication_attempts_pub_attempt_uq UNIQUE (publication_id, publication_run_at, attempt_no)
);

CREATE INDEX publication_attempts_publication_id_idx
    ON publication_attempts (publication_id, publication_run_at);

CREATE TABLE scheduled_actions (
    id                   UUID NOT NULL,
    publication_id       UUID NOT NULL,
    publication_run_at   TIMESTAMPTZ NOT NULL,
    workspace_id         UUID NOT NULL,
    kind                 TEXT NOT NULL,
    run_at               TIMESTAMPTZ NOT NULL,
    state                TEXT NOT NULL DEFAULT 'pending',
    attempts             INT NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ,
    locked_by            TEXT,
    locked_until         TIMESTAMPTZ,
    error_code           TEXT,
    error_message        TEXT,
    payload              JSONB NOT NULL DEFAULT '{}'::jsonb,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, run_at),
    CONSTRAINT scheduled_actions_kind_check
        CHECK (kind IN ('delete', 'unpin', 'first_comment', 'edit')),
    CONSTRAINT scheduled_actions_state_check CHECK (state IN (
        'pending', 'queued', 'in_progress', 'succeeded',
        'failed_retryable', 'failed_permanent', 'cancelled'
    )),
    CONSTRAINT scheduled_actions_publication_fk
        FOREIGN KEY (publication_id, publication_run_at)
        REFERENCES publications (id, run_at)
) PARTITION BY RANGE (run_at);

CREATE INDEX scheduled_actions_due_idx
    ON scheduled_actions (state, run_at)
    WHERE state IN ('pending', 'failed_retryable');

CREATE INDEX scheduled_actions_due_coalesce_idx
    ON scheduled_actions ((COALESCE(next_attempt_at, run_at)))
    WHERE state IN ('pending', 'failed_retryable');

CREATE INDEX scheduled_actions_publication_id_idx
    ON scheduled_actions (publication_id, publication_run_at);
CREATE INDEX scheduled_actions_workspace_id_idx ON scheduled_actions (workspace_id);

CREATE TABLE scheduled_actions_2026_10 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2026-10-01+00') TO ('2026-11-01+00');
CREATE TABLE scheduled_actions_2026_11 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2026-11-01+00') TO ('2026-12-01+00');
CREATE TABLE scheduled_actions_2026_12 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2026-12-01+00') TO ('2027-01-01+00');
CREATE TABLE scheduled_actions_2027_01 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-01-01+00') TO ('2027-02-01+00');
CREATE TABLE scheduled_actions_2027_02 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-02-01+00') TO ('2027-03-01+00');
CREATE TABLE scheduled_actions_2027_03 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-03-01+00') TO ('2027-04-01+00');

-- ---------------------------------------------------------------------------
-- Outbox / audit / notifications / idempotency
-- ---------------------------------------------------------------------------
CREATE TABLE outbox (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    aggregate     TEXT NOT NULL,
    aggregate_id  UUID NOT NULL,
    event_type    TEXT NOT NULL,
    payload       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL,
    actor_id      UUID,
    action        TEXT NOT NULL,
    entity        TEXT NOT NULL,
    entity_id     UUID,
    diff          JSONB,
    ip            INET,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_workspace_id_created_at_idx ON audit_log (workspace_id, created_at DESC);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    type        TEXT NOT NULL,
    payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX notifications_user_id_created_at_idx ON notifications (user_id, created_at DESC);
CREATE INDEX notifications_user_unread_idx ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE idempotency_requests (
    key            TEXT NOT NULL,
    user_id        UUID NOT NULL REFERENCES users (id),
    request_hash   TEXT NOT NULL,
    response       JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, user_id)
);

CREATE INDEX idempotency_requests_created_at_idx ON idempotency_requests (created_at);

-- ---------------------------------------------------------------------------
-- Billing runtime tables
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                          UUID PRIMARY KEY,
    workspace_id                UUID NOT NULL REFERENCES workspaces (id),
    plan_id                     UUID NOT NULL REFERENCES plans (id),
    status                      TEXT NOT NULL,
    period_start                TIMESTAMPTZ NOT NULL,
    period_end                  TIMESTAMPTZ NOT NULL,
    cancel_at_period_end        BOOLEAN NOT NULL DEFAULT FALSE,
    provider_payment_method_id  TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT subscriptions_status_check
        CHECK (status IN ('trialing', 'active', 'past_due', 'cancelled', 'expired'))
);

CREATE INDEX subscriptions_workspace_id_idx ON subscriptions (workspace_id);
CREATE INDEX subscriptions_plan_id_idx ON subscriptions (plan_id);

CREATE TABLE payments (
    id                   UUID PRIMARY KEY,
    workspace_id         UUID NOT NULL REFERENCES workspaces (id),
    provider             TEXT NOT NULL,
    provider_payment_id  TEXT NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             TEXT NOT NULL DEFAULT 'RUB',
    status               TEXT NOT NULL,
    receipt_id           TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT payments_provider_check CHECK (provider IN ('yookassa', 'tkassa')),
    CONSTRAINT payments_status_check
        CHECK (status IN ('pending', 'waiting_for_capture', 'succeeded', 'cancelled', 'refunded')),
    CONSTRAINT payments_provider_payment_id_uq UNIQUE (provider_payment_id)
);

CREATE INDEX payments_workspace_id_idx ON payments (workspace_id);

CREATE TABLE ledger_entries (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    amount        NUMERIC(12, 2) NOT NULL,
    kind          TEXT NOT NULL,
    ref_id        UUID,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ledger_entries_kind_check
        CHECK (kind IN ('charge', 'payment', 'refund', 'promo', 'referral'))
);

CREATE INDEX ledger_entries_workspace_id_idx ON ledger_entries (workspace_id, created_at DESC);

CREATE TABLE usage_counters (
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    period        TEXT NOT NULL,
    metric        TEXT NOT NULL,
    value         BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, period, metric),
    CONSTRAINT usage_counters_metric_check
        CHECK (metric IN ('posts', 'ai_credits'))
);
