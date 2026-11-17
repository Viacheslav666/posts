-- BE-8: media library columns, indexes, and plan storage quotas.
-- Expand/contract friendly: ADD COLUMN nullable or with defaults; jsonb || merge.

ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS filename TEXT,
    ADD COLUMN IF NOT EXISTS folder TEXT,
    ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS original_url TEXT;

CREATE INDEX IF NOT EXISTS media_assets_folder_idx
    ON media_assets (workspace_id, folder)
    WHERE deleted_at IS NULL AND folder IS NOT NULL;

CREATE INDEX IF NOT EXISTS media_assets_tags_gin_idx
    ON media_assets USING GIN (tags);

CREATE INDEX IF NOT EXISTS media_assets_status_idx
    ON media_assets (workspace_id, status)
    WHERE deleted_at IS NULL;

-- storage_bytes quotas (bytes): free 1 GiB, start 10 GiB, pro 50 GiB, agency 200 GiB
UPDATE plans
SET limits = limits || '{"storage_bytes": 1073741824}'::jsonb
WHERE code = 'free';

UPDATE plans
SET limits = limits || '{"storage_bytes": 10737418240}'::jsonb
WHERE code = 'start';

UPDATE plans
SET limits = limits || '{"storage_bytes": 53687091200}'::jsonb
WHERE code = 'pro';

UPDATE plans
SET limits = limits || '{"storage_bytes": 214748364800}'::jsonb
WHERE code = 'agency';
