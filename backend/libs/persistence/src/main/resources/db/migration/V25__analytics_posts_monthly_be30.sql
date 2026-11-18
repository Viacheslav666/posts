-- BE-30: materialised monthly post analytics (parity with vposter /posts/stats).
-- Expand-only. Aggregates are written by the analytics job; API reads this table only.

CREATE TABLE IF NOT EXISTS analytics_posts_monthly (
    workspace_id UUID NOT NULL,
    year INT NOT NULL,
    month INT NOT NULL,
    regular_count BIGINT NOT NULL DEFAULT 0,
    ad_count BIGINT NOT NULL DEFAULT 0,
    repost_count BIGINT NOT NULL DEFAULT 0,
    network_succeeded JSONB NOT NULL DEFAULT '{}'::jsonb,
    network_failed JSONB NOT NULL DEFAULT '{}'::jsonb,
    top_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    revenue_with_vat NUMERIC(18, 2) NOT NULL DEFAULT 0,
    revenue_without_vat NUMERIC(18, 2) NOT NULL DEFAULT 0,
    revenue_vat NUMERIC(18, 2) NOT NULL DEFAULT 0,
    revenue_fee_3pct NUMERIC(18, 2) NOT NULL DEFAULT 0,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, year, month),
    CONSTRAINT analytics_posts_monthly_month_check CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT analytics_posts_monthly_year_check CHECK (year BETWEEN 2000 AND 2100)
);

CREATE INDEX IF NOT EXISTS analytics_posts_monthly_workspace_year_idx
    ON analytics_posts_monthly (workspace_id, year);

-- Singleton watermark for incremental, idempotent recompute of dirty months.
CREATE TABLE IF NOT EXISTS analytics_posts_job_state (
    id INT PRIMARY KEY DEFAULT 1,
    last_watermark TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ '1970-01-01 00:00:00+00',
    last_success_at TIMESTAMPTZ,
    last_months_recomputed INT NOT NULL DEFAULT 0,
    CONSTRAINT analytics_posts_job_state_singleton CHECK (id = 1)
);

INSERT INTO analytics_posts_job_state (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;
