-- BE-24: ffprobe / media probe metadata for story validation (expand-only).
ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS meta JSONB NOT NULL DEFAULT '{}'::jsonb;
