-- BE-12b / BE-31: workspace wallet + daily balance billing (expand-only, Squawk-safe).
-- Spec: docs/product/billing-rules.md v2.0, docs/specs/02-backend.md §9
-- Decision 2026-11-18: balance + daily charge instead of subscription (legacy kept).

-- ---------------------------------------------------------------------------
-- plans: daily price + billing model
-- ---------------------------------------------------------------------------
ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS price_day_kopecks BIGINT NOT NULL DEFAULT 0;

ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS billing_model TEXT NOT NULL DEFAULT 'subscription';

ALTER TABLE plans
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE plans
    ADD CONSTRAINT plans_billing_model_check
    CHECK (billing_model IN ('balance', 'subscription')) NOT VALID;
ALTER TABLE plans VALIDATE CONSTRAINT plans_billing_model_check;

ALTER TABLE plans
    ADD CONSTRAINT plans_price_day_kopecks_nonneg_check
    CHECK (price_day_kopecks >= 0) NOT VALID;
ALTER TABLE plans VALIDATE CONSTRAINT plans_price_day_kopecks_nonneg_check;

-- ---------------------------------------------------------------------------
-- workspaces: per-workspace billing model
-- ---------------------------------------------------------------------------
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS billing_model TEXT NOT NULL DEFAULT 'balance';

ALTER TABLE workspaces
    ADD CONSTRAINT workspaces_billing_model_check
    CHECK (billing_model IN ('balance', 'subscription')) NOT VALID;
ALTER TABLE workspaces VALIDATE CONSTRAINT workspaces_billing_model_check;

-- ---------------------------------------------------------------------------
-- posts: pause reason for billing freeze (scheduled posts not lost)
-- ---------------------------------------------------------------------------
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS pause_reason TEXT;

ALTER TABLE posts
    ADD CONSTRAINT posts_pause_reason_check
    CHECK (pause_reason IS NULL OR pause_reason IN ('billing', 'user')) NOT VALID;
ALTER TABLE posts VALIDATE CONSTRAINT posts_pause_reason_check;

-- ---------------------------------------------------------------------------
-- wallets
-- ---------------------------------------------------------------------------
CREATE TABLE wallets (
    workspace_id            UUID PRIMARY KEY REFERENCES workspaces (id),
    balance_kopecks         BIGINT NOT NULL DEFAULT 0,
    bonus_balance_kopecks   BIGINT NOT NULL DEFAULT 0,
    version                 BIGINT NOT NULL DEFAULT 0,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE wallets
    ADD CONSTRAINT wallets_bonus_lte_balance_check
    CHECK (
        bonus_balance_kopecks >= 0
        AND balance_kopecks >= 0
        AND bonus_balance_kopecks <= balance_kopecks
    ) NOT VALID;
ALTER TABLE wallets VALIDATE CONSTRAINT wallets_bonus_lte_balance_check;

-- ---------------------------------------------------------------------------
-- ledger_entries: kopecks + idempotency + bonus flag (keep legacy amount/kind)
-- ---------------------------------------------------------------------------
ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS amount_kopecks BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS is_bonus BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'RUB';

-- Backfill amount_kopecks from legacy NUMERIC amount (rubles → kopecks)
UPDATE ledger_entries
SET amount_kopecks = ROUND(amount * 100)::BIGINT
WHERE amount_kopecks = 0 AND amount <> 0;

CREATE UNIQUE INDEX IF NOT EXISTS ledger_entries_idempotency_key_uq
    ON ledger_entries (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ledger_entries_workspace_created_kopecks_idx
    ON ledger_entries (workspace_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- daily_charges: exactly one charge per workspace per calendar day (MSK date)
-- ---------------------------------------------------------------------------
CREATE TABLE daily_charges (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL REFERENCES workspaces (id),
    charge_date         DATE NOT NULL,
    amount_kopecks      BIGINT NOT NULL,
    plan_id             UUID REFERENCES plans (id),
    channels_counted    INT NOT NULL DEFAULT 0,
    ledger_entry_id     UUID REFERENCES ledger_entries (id),
    status              TEXT NOT NULL DEFAULT 'charged',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT daily_charges_workspace_date_uq UNIQUE (workspace_id, charge_date)
);

ALTER TABLE daily_charges
    ADD CONSTRAINT daily_charges_status_check
    CHECK (status IN ('charged', 'skipped_free', 'skipped_frozen', 'failed')) NOT VALID;
ALTER TABLE daily_charges VALIDATE CONSTRAINT daily_charges_status_check;

ALTER TABLE daily_charges
    ADD CONSTRAINT daily_charges_amount_nonneg_check
    CHECK (amount_kopecks >= 0) NOT VALID;
ALTER TABLE daily_charges VALIDATE CONSTRAINT daily_charges_amount_nonneg_check;

CREATE INDEX IF NOT EXISTS daily_charges_date_idx ON daily_charges (charge_date);

-- ---------------------------------------------------------------------------
-- autopay_settings
-- ---------------------------------------------------------------------------
CREATE TABLE autopay_settings (
    workspace_id                    UUID PRIMARY KEY REFERENCES workspaces (id),
    enabled                         BOOLEAN NOT NULL DEFAULT FALSE,
    threshold_kopecks               BIGINT NOT NULL DEFAULT 10000,
    topup_amount_kopecks            BIGINT NOT NULL DEFAULT 50000,
    provider_payment_method_id      TEXT,
    card_mask                       TEXT,
    last_triggered_at               TIMESTAMPTZ,
    last_payment_id                 UUID REFERENCES payments (id),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE autopay_settings
    ADD CONSTRAINT autopay_threshold_positive_check
    CHECK (threshold_kopecks > 0) NOT VALID;
ALTER TABLE autopay_settings VALIDATE CONSTRAINT autopay_threshold_positive_check;

ALTER TABLE autopay_settings
    ADD CONSTRAINT autopay_topup_positive_check
    CHECK (topup_amount_kopecks > 0) NOT VALID;
ALTER TABLE autopay_settings VALIDATE CONSTRAINT autopay_topup_positive_check;

-- ---------------------------------------------------------------------------
-- payments: purpose (topup / autopay / subscription / invoice)
-- ---------------------------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS purpose TEXT NOT NULL DEFAULT 'subscription';

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS amount_kopecks BIGINT NOT NULL DEFAULT 0;

UPDATE payments
SET amount_kopecks = ROUND(amount * 100)::BIGINT
WHERE amount_kopecks = 0 AND amount <> 0;

ALTER TABLE payments
    ADD CONSTRAINT payments_purpose_check
    CHECK (purpose IN ('topup', 'autopay', 'subscription', 'invoice')) NOT VALID;
ALTER TABLE payments VALIDATE CONSTRAINT payments_purpose_check;

-- ---------------------------------------------------------------------------
-- invoices: buyer requisites + act
-- ---------------------------------------------------------------------------
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS buyer_requisites JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS act_pdf_url TEXT;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS invoice_number TEXT;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS amount_kopecks BIGINT NOT NULL DEFAULT 0;

UPDATE invoices
SET amount_kopecks = ROUND(amount * 100)::BIGINT
WHERE amount_kopecks = 0 AND amount <> 0;

CREATE UNIQUE INDEX IF NOT EXISTS invoices_invoice_number_uq
    ON invoices (invoice_number)
    WHERE invoice_number IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Seed balance-mode tariff grid (vposter defaults). Limits aligned with BE-12 tiers.
-- ---------------------------------------------------------------------------
INSERT INTO plans (id, code, name, price_month, price_year, price_day_kopecks, billing_model, active, limits)
VALUES
    (
        '019a0000-0001-7000-8000-000000000101',
        'daily_6',
        'До 6 каналов',
        390,
        0,
        1300,
        'balance',
        TRUE,
        '{"channels": 6, "seats": 1, "posts_month": null, "ai_credits": 200, "grabber_rules": 1, "stories_month": 30}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000102',
        'daily_12',
        'До 12 каналов',
        510,
        0,
        1700,
        'balance',
        TRUE,
        '{"channels": 12, "seats": 2, "posts_month": null, "ai_credits": 500, "grabber_rules": 3, "stories_month": 60}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000103',
        'daily_25',
        'До 25 каналов',
        810,
        0,
        2700,
        'balance',
        TRUE,
        '{"channels": 25, "seats": 3, "posts_month": null, "ai_credits": 1000, "grabber_rules": 5, "stories_month": 100}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000104',
        'daily_50',
        'До 50 каналов',
        1110,
        0,
        3700,
        'balance',
        TRUE,
        '{"channels": 50, "seats": 5, "posts_month": null, "ai_credits": 2000, "grabber_rules": 10, "stories_month": 200}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000105',
        'daily_75',
        'До 75 каналов',
        1410,
        0,
        4700,
        'balance',
        TRUE,
        '{"channels": 75, "seats": 8, "posts_month": null, "ai_credits": 3000, "grabber_rules": 15, "stories_month": 300}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000106',
        'daily_100',
        'До 100 каналов',
        1710,
        0,
        5700,
        'balance',
        TRUE,
        '{"channels": 100, "seats": 10, "posts_month": null, "ai_credits": 5000, "grabber_rules": 20, "stories_month": 500}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000107',
        'daily_200',
        'До 200 каналов',
        2910,
        0,
        9700,
        'balance',
        TRUE,
        '{"channels": 200, "seats": 15, "posts_month": null, "ai_credits": 8000, "grabber_rules": 30, "stories_month": 800}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000108',
        'daily_500',
        'До 500 каналов',
        6510,
        0,
        21700,
        'balance',
        TRUE,
        '{"channels": 500, "seats": 25, "posts_month": null, "ai_credits": 15000, "grabber_rules": 50, "stories_month": 1500}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000109',
        'daily_1000',
        'До 1000 каналов',
        12510,
        0,
        41700,
        'balance',
        TRUE,
        '{"channels": 1000, "seats": 50, "posts_month": null, "ai_credits": 30000, "grabber_rules": 100, "stories_month": 3000}'::jsonb
    )
ON CONFLICT (code) DO NOTHING;

-- Mark free plan usable in balance mode (0 ₽/day); keep subscription plans for legacy.
UPDATE plans
SET price_day_kopecks = 0,
    billing_model = 'balance',
    active = TRUE
WHERE code = 'free';

UPDATE plans
SET price_day_kopecks = ROUND(price_month * 100 / 30)::BIGINT
WHERE billing_model = 'subscription' AND code IN ('start', 'pro', 'agency');
