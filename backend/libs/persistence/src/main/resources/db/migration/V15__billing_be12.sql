-- BE-12: billing / YooKassa foundation (expand-only, Squawk-safe).
-- Spec: docs/product/billing-rules.md, docs/specs/02-backend.md §4 billing

-- ---------------------------------------------------------------------------
-- users: one trial per account
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS trial_used BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------------
-- payments: plan / period / promo / confirmation / metadata / idempotence
-- ---------------------------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES plans (id);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS billing_period TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS promo_code TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS confirmation_url TEXT;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS idempotence_key TEXT;

ALTER TABLE payments
    ADD CONSTRAINT payments_billing_period_check
    CHECK (
        billing_period IS NULL
        OR billing_period IN ('month', 'year')
    ) NOT VALID;
ALTER TABLE payments VALIDATE CONSTRAINT payments_billing_period_check;

-- ---------------------------------------------------------------------------
-- subscriptions: period, pending plan, grace / freeze
-- ---------------------------------------------------------------------------
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS billing_period TEXT;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS pending_plan_id UUID REFERENCES plans (id);

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS grace_ends_at TIMESTAMPTZ;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS frozen_at TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- promo_codes
-- ---------------------------------------------------------------------------
CREATE TABLE promo_codes (
    id                   UUID PRIMARY KEY,
    code                 TEXT UNIQUE NOT NULL,
    kind                 TEXT NOT NULL,
    value                NUMERIC(12, 2) NOT NULL,
    valid_from           TIMESTAMPTZ NOT NULL,
    valid_until          TIMESTAMPTZ NOT NULL,
    max_redemptions      INT,
    redemption_count     INT NOT NULL DEFAULT 0,
    stackable            BOOLEAN NOT NULL DEFAULT FALSE,
    once_per_workspace   BOOLEAN NOT NULL DEFAULT TRUE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE promo_codes
    ADD CONSTRAINT promo_codes_kind_check
    CHECK (kind IN ('percent', 'fixed', 'trial_days')) NOT VALID;
ALTER TABLE promo_codes VALIDATE CONSTRAINT promo_codes_kind_check;

-- ---------------------------------------------------------------------------
-- promo_redemptions
-- ---------------------------------------------------------------------------
CREATE TABLE promo_redemptions (
    id               UUID PRIMARY KEY,
    promo_code_id    UUID NOT NULL REFERENCES promo_codes (id),
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    user_id          UUID REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT promo_redemptions_promo_workspace_uq UNIQUE (promo_code_id, workspace_id)
);

CREATE INDEX promo_redemptions_workspace_id_idx ON promo_redemptions (workspace_id);
CREATE INDEX promo_redemptions_promo_code_id_idx ON promo_redemptions (promo_code_id);

-- ---------------------------------------------------------------------------
-- invoices (billing, not ORD)
-- ---------------------------------------------------------------------------
CREATE TABLE invoices (
    id               UUID PRIMARY KEY,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    payment_id       UUID REFERENCES payments (id),
    amount           NUMERIC(12, 2) NOT NULL,
    currency         TEXT NOT NULL DEFAULT 'RUB',
    status           TEXT NOT NULL,
    pdf_url          TEXT,
    description      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX invoices_workspace_id_idx ON invoices (workspace_id);
CREATE INDEX invoices_payment_id_idx ON invoices (payment_id) WHERE payment_id IS NOT NULL;

ALTER TABLE invoices
    ADD CONSTRAINT invoices_status_check
    CHECK (status IN ('draft', 'issued', 'paid', 'cancelled', 'void')) NOT VALID;
ALTER TABLE invoices VALIDATE CONSTRAINT invoices_status_check;

-- ---------------------------------------------------------------------------
-- billing_webhook_inbox (YooKassa / provider events)
-- ---------------------------------------------------------------------------
CREATE TABLE billing_webhook_inbox (
    id                   UUID PRIMARY KEY,
    event_id             TEXT NOT NULL,
    event_type           TEXT NOT NULL,
    provider_payment_id  TEXT,
    payload              JSONB NOT NULL,
    status               TEXT NOT NULL DEFAULT 'pending',
    error_message        TEXT,
    received_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at         TIMESTAMPTZ,
    CONSTRAINT billing_webhook_inbox_event_id_uq UNIQUE (event_id)
);

ALTER TABLE billing_webhook_inbox
    ADD CONSTRAINT billing_webhook_inbox_status_check
    CHECK (status IN ('pending', 'processed', 'ignored', 'failed')) NOT VALID;
ALTER TABLE billing_webhook_inbox VALIDATE CONSTRAINT billing_webhook_inbox_status_check;

CREATE INDEX billing_webhook_inbox_pending_idx
    ON billing_webhook_inbox (status, received_at)
    WHERE status = 'pending';

-- ---------------------------------------------------------------------------
-- referrals (minimal stub for BE-12)
-- ---------------------------------------------------------------------------
CREATE TABLE referrals (
    id                 UUID PRIMARY KEY,
    referrer_user_id   UUID REFERENCES users (id),
    referred_user_id   UUID REFERENCES users (id),
    code               TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX referrals_referrer_user_id_idx ON referrals (referrer_user_id);
CREATE INDEX referrals_referred_user_id_idx ON referrals (referred_user_id);

-- ---------------------------------------------------------------------------
-- plans.limits: grabber_rules + stories_month (merge, keep existing keys)
-- ---------------------------------------------------------------------------
UPDATE plans
SET limits = limits || '{"grabber_rules": 0, "stories_month": 0}'::jsonb
WHERE code = 'free';

UPDATE plans
SET limits = limits || '{"grabber_rules": 1, "stories_month": 30}'::jsonb
WHERE code = 'start';

UPDATE plans
SET limits = limits || '{"grabber_rules": 5, "stories_month": 100}'::jsonb
WHERE code = 'pro';

UPDATE plans
SET limits = limits || '{"grabber_rules": 20, "stories_month": 500}'::jsonb
WHERE code = 'agency';
