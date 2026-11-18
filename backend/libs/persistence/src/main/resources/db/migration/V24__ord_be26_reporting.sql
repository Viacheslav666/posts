-- BE-26: ORD reporting lifecycle (expand-only).
-- Soft-delete invoices, statistics provenance, report-run retries, creative display window.

ALTER TABLE ord_invoices
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE ord_statistics
    ADD COLUMN IF NOT EXISTS views_source TEXT;

ALTER TABLE ord_report_runs
    ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;

ALTER TABLE ord_report_runs
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

ALTER TABLE ord_creatives
    ADD COLUMN IF NOT EXISTS display_started_at TIMESTAMPTZ;

ALTER TABLE ord_creatives
    ADD COLUMN IF NOT EXISTS display_ended_at TIMESTAMPTZ;

ALTER TABLE ord_creatives
    ADD COLUMN IF NOT EXISTS final_stats_pending BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ord_creatives
    ADD COLUMN IF NOT EXISTS expiry_warning_sent_at TIMESTAMPTZ;

-- Reporting table filters (period + status + creative).
CREATE INDEX IF NOT EXISTS ord_statistics_workspace_period_status_idx
    ON ord_statistics (workspace_id, period_year, period_month, status);

CREATE INDEX IF NOT EXISTS ord_statistics_creative_period_idx
    ON ord_statistics (creative_id, period_year, period_month);

CREATE INDEX IF NOT EXISTS ord_invoices_workspace_active_period_idx
    ON ord_invoices (workspace_id, period_year, period_month)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ord_report_runs_retry_idx
    ON ord_report_runs (next_retry_at)
    WHERE status = 'error' AND next_retry_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS ord_creatives_display_window_idx
    ON ord_creatives (workspace_id, display_started_at, display_ended_at)
    WHERE status = 'registered';

CREATE INDEX IF NOT EXISTS ord_creatives_final_stats_pending_idx
    ON ord_creatives (workspace_id, provider)
    WHERE final_stats_pending = TRUE;

ALTER TABLE ord_statistics
    ADD CONSTRAINT ord_statistics_views_source_check
    CHECK (
        views_source IS NULL
        OR views_source IN ('vk_api', 'ok_api', 'story_api', 'override', 'unavailable', 'manual')
    ) NOT VALID;
ALTER TABLE ord_statistics VALIDATE CONSTRAINT ord_statistics_views_source_check;
