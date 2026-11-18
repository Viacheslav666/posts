-- BE-16: scheduled stories — channel story-connect + publication meta (expand-only).

-- ---------------------------------------------------------------------------
-- channels: VK stories require a separate community stories scope connection
-- ---------------------------------------------------------------------------
ALTER TABLE channels
    ADD COLUMN IF NOT EXISTS story_connection_status TEXT NOT NULL DEFAULT 'missing',
    ADD COLUMN IF NOT EXISTS story_token_enc BYTEA,
    ADD COLUMN IF NOT EXISTS story_connected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS story_connection_error TEXT;

ALTER TABLE channels
    ADD CONSTRAINT channels_story_connection_status_check
    CHECK (
        story_connection_status IN ('missing', 'pending', 'ready', 'revoked', 'error')
    ) NOT VALID;
ALTER TABLE channels VALIDATE CONSTRAINT channels_story_connection_status_check;

-- OK / non-VK: no separate story connect — leave default 'missing'; product code
-- treats OK as ready when channel.status=active (see StoryChannelPolicy).

CREATE INDEX IF NOT EXISTS channels_ws_story_status_idx
    ON channels (workspace_id, story_connection_status)
    WHERE deleted_at IS NULL AND network = 'vk';

-- ---------------------------------------------------------------------------
-- publications: optional meta (effective_ttl for OK Moments, story diagnostics)
-- ---------------------------------------------------------------------------
ALTER TABLE publications
    ADD COLUMN IF NOT EXISTS meta JSONB NOT NULL DEFAULT '{}'::jsonb;
