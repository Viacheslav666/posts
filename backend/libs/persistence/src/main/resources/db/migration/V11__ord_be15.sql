-- BE-15: ORD / ad marking tables (expand-only, Squawk-safe).
-- Spec: docs/product/features/scheduled-posts-and-ad-marking.md §5
-- FK from posts.ord_creative_id is soft (no ALTER ADD FK on live posts) — integrity in services.

-- ---------------------------------------------------------------------------
-- Connections (client API key, envelope-encrypted — ADR-0006)
-- ---------------------------------------------------------------------------
CREATE TABLE ord_connections (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    provider         TEXT NOT NULL,
    api_key_enc      BYTEA NOT NULL,
    publisher_inn    TEXT,
    publisher_name   TEXT,
    org_type         TEXT,
    phone            TEXT,
    roles            JSONB NOT NULL DEFAULT '[]'::jsonb,
    status           TEXT NOT NULL DEFAULT 'draft',
    last_error       TEXT,
    is_default       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ord_connections_workspace_provider_uq UNIQUE (workspace_id, provider)
);

CREATE INDEX ord_connections_workspace_id_idx ON ord_connections (workspace_id);

ALTER TABLE ord_connections
    ADD CONSTRAINT ord_connections_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_connections VALIDATE CONSTRAINT ord_connections_provider_check;

ALTER TABLE ord_connections
    ADD CONSTRAINT ord_connections_status_check
    CHECK (status IN ('draft', 'active', 'error', 'disabled')) NOT VALID;
ALTER TABLE ord_connections VALIDATE CONSTRAINT ord_connections_status_check;

ALTER TABLE ord_connections
    ADD CONSTRAINT ord_connections_org_type_check
    CHECK (
        org_type IS NULL
        OR org_type IN (
            'physical', 'ip', 'juridical',
            'foreign_physical', 'foreign_juridical'
        )
    ) NOT VALID;
ALTER TABLE ord_connections VALIDATE CONSTRAINT ord_connections_org_type_check;

-- ---------------------------------------------------------------------------
-- Contractors (advertisers / counterparties)
-- ---------------------------------------------------------------------------
CREATE TABLE ord_contractors (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    inn              TEXT NOT NULL,
    kpp              TEXT NOT NULL DEFAULT '',
    name             TEXT NOT NULL,
    org_type         TEXT NOT NULL,
    country_oksm     TEXT NOT NULL DEFAULT '643',
    phone            TEXT,
    address          TEXT,
    meta             JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider_refs    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ord_contractors_workspace_inn_kpp_uq UNIQUE (workspace_id, inn, kpp)
);

CREATE INDEX ord_contractors_workspace_id_idx ON ord_contractors (workspace_id);
CREATE INDEX ord_contractors_workspace_inn_idx ON ord_contractors (workspace_id, inn);

ALTER TABLE ord_contractors
    ADD CONSTRAINT ord_contractors_org_type_check
    CHECK (org_type IN (
        'physical', 'ip', 'juridical',
        'foreign_physical', 'foreign_juridical'
    )) NOT VALID;
ALTER TABLE ord_contractors VALIDATE CONSTRAINT ord_contractors_org_type_check;

-- ---------------------------------------------------------------------------
-- Contracts
-- ---------------------------------------------------------------------------
CREATE TABLE ord_contracts (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    contractor_id    UUID NOT NULL REFERENCES ord_contractors (id),
    external_id      TEXT NOT NULL,
    serial           TEXT,
    date_start       DATE,
    date_end         DATE,
    amount           NUMERIC(18, 2),
    vat_rate         TEXT NOT NULL DEFAULT 'without_vat',
    provider         TEXT NOT NULL,
    provider_ref     TEXT,
    self_promo       BOOLEAN NOT NULL DEFAULT FALSE,
    status           TEXT NOT NULL DEFAULT 'draft',
    meta             JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ord_contracts_workspace_external_uq UNIQUE (workspace_id, provider, external_id)
);

CREATE INDEX ord_contracts_workspace_id_idx ON ord_contracts (workspace_id);
CREATE INDEX ord_contracts_contractor_id_idx ON ord_contracts (contractor_id);

ALTER TABLE ord_contracts
    ADD CONSTRAINT ord_contracts_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_contracts VALIDATE CONSTRAINT ord_contracts_provider_check;

ALTER TABLE ord_contracts
    ADD CONSTRAINT ord_contracts_status_check
    CHECK (status IN ('draft', 'synced', 'error')) NOT VALID;
ALTER TABLE ord_contracts VALIDATE CONSTRAINT ord_contracts_status_check;

-- ---------------------------------------------------------------------------
-- Creatives (+ erid)
-- ---------------------------------------------------------------------------
CREATE TABLE ord_creatives (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    contract_id      UUID REFERENCES ord_contracts (id),
    post_id          UUID,
    connection_id    UUID REFERENCES ord_connections (id),
    external_id      TEXT NOT NULL,
    erid             TEXT,
    provider         TEXT NOT NULL,
    kktu             TEXT[] NOT NULL DEFAULT '{}',
    media_refs       JSONB NOT NULL DEFAULT '[]'::jsonb,
    advertiser_name  TEXT,
    advertiser_inn   TEXT,
    status           TEXT NOT NULL DEFAULT 'draft',
    registered_at    TIMESTAMPTZ,
    last_error       TEXT,
    display_period_days INT NOT NULL DEFAULT 30,
    auto_delete_after_period BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ord_creatives_workspace_external_uq UNIQUE (workspace_id, provider, external_id)
);

CREATE INDEX ord_creatives_workspace_id_idx ON ord_creatives (workspace_id);
CREATE INDEX ord_creatives_post_id_idx ON ord_creatives (post_id) WHERE post_id IS NOT NULL;
CREATE INDEX ord_creatives_erid_idx ON ord_creatives (erid) WHERE erid IS NOT NULL;
CREATE INDEX ord_creatives_status_idx ON ord_creatives (workspace_id, status);

ALTER TABLE ord_creatives
    ADD CONSTRAINT ord_creatives_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_creatives VALIDATE CONSTRAINT ord_creatives_provider_check;

ALTER TABLE ord_creatives
    ADD CONSTRAINT ord_creatives_status_check
    CHECK (status IN ('draft', 'registering', 'registered', 'error')) NOT VALID;
ALTER TABLE ord_creatives VALIDATE CONSTRAINT ord_creatives_status_check;

-- ---------------------------------------------------------------------------
-- Platforms / invoices (acts) / statistics
-- ---------------------------------------------------------------------------
CREATE TABLE ord_platforms (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    provider         TEXT NOT NULL,
    external_id      TEXT NOT NULL,
    name             TEXT,
    url              TEXT,
    network          TEXT,
    meta             JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ord_platforms_workspace_external_uq UNIQUE (workspace_id, provider, external_id)
);

CREATE INDEX ord_platforms_workspace_id_idx ON ord_platforms (workspace_id);

ALTER TABLE ord_platforms
    ADD CONSTRAINT ord_platforms_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_platforms VALIDATE CONSTRAINT ord_platforms_provider_check;

CREATE TABLE ord_invoices (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    connection_id    UUID REFERENCES ord_connections (id),
    provider         TEXT NOT NULL,
    external_id      TEXT NOT NULL,
    period_year      INT NOT NULL,
    period_month     INT NOT NULL,
    contract_id      UUID REFERENCES ord_contracts (id),
    amount           NUMERIC(18, 2),
    status           TEXT NOT NULL DEFAULT 'draft',
    provider_ref     TEXT,
    last_error       TEXT,
    submitted_at     TIMESTAMPTZ,
    meta             JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ord_invoices_period_uq UNIQUE (workspace_id, provider, period_year, period_month, external_id)
);

CREATE INDEX ord_invoices_workspace_period_idx
    ON ord_invoices (workspace_id, period_year, period_month);

ALTER TABLE ord_invoices
    ADD CONSTRAINT ord_invoices_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_invoices VALIDATE CONSTRAINT ord_invoices_provider_check;

ALTER TABLE ord_invoices
    ADD CONSTRAINT ord_invoices_status_check
    CHECK (status IN ('draft', 'submitted', 'error', 'verified')) NOT VALID;
ALTER TABLE ord_invoices VALIDATE CONSTRAINT ord_invoices_status_check;

ALTER TABLE ord_invoices
    ADD CONSTRAINT ord_invoices_period_month_check
    CHECK (period_month BETWEEN 1 AND 12) NOT VALID;
ALTER TABLE ord_invoices VALIDATE CONSTRAINT ord_invoices_period_month_check;

CREATE TABLE ord_statistics (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    creative_id      UUID NOT NULL REFERENCES ord_creatives (id),
    platform_id      UUID REFERENCES ord_platforms (id),
    provider         TEXT NOT NULL,
    period_year      INT NOT NULL,
    period_month     INT NOT NULL,
    imps_fact        BIGINT NOT NULL DEFAULT 0,
    amount           NUMERIC(18, 2),
    status           TEXT NOT NULL DEFAULT 'draft',
    submitted_at     TIMESTAMPTZ,
    meta             JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ord_statistics_period_uq
    ON ord_statistics (
        workspace_id,
        creative_id,
        (COALESCE(platform_id, '00000000-0000-0000-0000-000000000000'::uuid)),
        period_year,
        period_month
    );

CREATE INDEX ord_statistics_workspace_period_idx
    ON ord_statistics (workspace_id, period_year, period_month);
CREATE INDEX ord_statistics_creative_id_idx ON ord_statistics (creative_id);

ALTER TABLE ord_statistics
    ADD CONSTRAINT ord_statistics_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_statistics VALIDATE CONSTRAINT ord_statistics_provider_check;

ALTER TABLE ord_statistics
    ADD CONSTRAINT ord_statistics_status_check
    CHECK (status IN ('draft', 'submitted', 'error')) NOT VALID;
ALTER TABLE ord_statistics VALIDATE CONSTRAINT ord_statistics_status_check;

ALTER TABLE ord_statistics
    ADD CONSTRAINT ord_statistics_period_month_check
    CHECK (period_month BETWEEN 1 AND 12) NOT VALID;
ALTER TABLE ord_statistics VALIDATE CONSTRAINT ord_statistics_period_month_check;

-- ---------------------------------------------------------------------------
-- Dictionary caches (ККТУ / ОКСМ)
-- ---------------------------------------------------------------------------
CREATE TABLE ord_dictionary_kktu (
    code             TEXT PRIMARY KEY,
    name             TEXT NOT NULL,
    parent_code      TEXT,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ord_dictionary_oksm (
    code             TEXT PRIMARY KEY,
    name             TEXT NOT NULL,
    alpha2           TEXT,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ord_dictionary_kktu_name_idx ON ord_dictionary_kktu (name);
CREATE INDEX ord_dictionary_oksm_name_idx ON ord_dictionary_oksm (name);

-- Seed minimal KKTU / OKSМ for local/tests (expand later via sync job).
INSERT INTO ord_dictionary_oksm (code, name, alpha2) VALUES
    ('643', 'Российская Федерация', 'RU'),
    ('112', 'Республика Беларусь', 'BY'),
    ('398', 'Республика Казахстан', 'KZ'),
    ('804', 'Украина', 'UA'),
    ('860', 'Республика Узбекистан', 'UZ')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ord_dictionary_kktu (code, name, parent_code) VALUES
    ('1.1.1', 'Товары / услуги общего назначения', NULL),
    ('7.1.1', 'Финансовые услуги', NULL),
    ('20.1', 'Информационные технологии', NULL),
    ('28.1.7', 'Розничная торговля / маркетплейсы', NULL),
    ('30.15.1', 'Реклама / медиа', NULL)
ON CONFLICT (code) DO NOTHING;

-- Report run ledger (idempotency for monthly job / manual run)
CREATE TABLE ord_report_runs (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    provider         TEXT NOT NULL,
    period_year      INT NOT NULL,
    period_month     INT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'running',
    invoices_count   INT NOT NULL DEFAULT 0,
    statistics_count INT NOT NULL DEFAULT 0,
    last_error       TEXT,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    CONSTRAINT ord_report_runs_period_uq UNIQUE (workspace_id, provider, period_year, period_month)
);

ALTER TABLE ord_report_runs
    ADD CONSTRAINT ord_report_runs_provider_check
    CHECK (provider IN ('vk_ord', 'yandex_ord', 'ozon_ord')) NOT VALID;
ALTER TABLE ord_report_runs VALIDATE CONSTRAINT ord_report_runs_provider_check;

ALTER TABLE ord_report_runs
    ADD CONSTRAINT ord_report_runs_status_check
    CHECK (status IN ('running', 'succeeded', 'error')) NOT VALID;
ALTER TABLE ord_report_runs VALIDATE CONSTRAINT ord_report_runs_status_check;
