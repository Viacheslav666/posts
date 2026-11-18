-- BE-27: grabber near-duplicate SimHash + indexes (expand-only, Squawk-safe).

ALTER TABLE grabber_items
    ADD COLUMN IF NOT EXISTS simhash BIGINT;

-- Recent-N similarity lookback within a rule
CREATE INDEX IF NOT EXISTS grabber_items_rule_created_simhash_idx
    ON grabber_items (rule_id, created_at DESC)
    WHERE simhash IS NOT NULL
      AND status NOT IN ('rejected', 'suppressed_dedup', 'filtered_out');

-- Channel-scoped lookback (dedup_scope=channel)
CREATE INDEX IF NOT EXISTS grabber_items_channel_created_simhash_idx
    ON grabber_items (workspace_id, target_channel_id, created_at DESC)
    WHERE simhash IS NOT NULL
      AND status NOT IN ('rejected', 'suppressed_dedup', 'filtered_out');
