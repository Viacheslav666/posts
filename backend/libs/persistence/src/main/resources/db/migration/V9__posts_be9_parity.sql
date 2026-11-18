-- BE-9: posts calendar indexes + parity scaffolding for BE-15/16/17 (expand-only).
-- Does not implement ORD/stories/grabber flows; only schema/fields so later tasks
-- need not rewrite the posts core.

-- ---------------------------------------------------------------------------
-- posts: content kind, source, ad snapshot, story placeholders, schedule TZ
-- ---------------------------------------------------------------------------
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS content_kind TEXT NOT NULL DEFAULT 'post',
    ADD COLUMN IF NOT EXISTS source_type TEXT NOT NULL DEFAULT 'manual',
    ADD COLUMN IF NOT EXISTS source_ref JSONB,
    ADD COLUMN IF NOT EXISTS ad_payload JSONB,
    ADD COLUMN IF NOT EXISTS stop_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS remove_after_publish BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS story_ttl TEXT,
    ADD COLUMN IF NOT EXISTS story_link_text TEXT,
    ADD COLUMN IF NOT EXISTS story_link_url TEXT,
    ADD COLUMN IF NOT EXISTS story_options JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS moderation_status TEXT,
    ADD COLUMN IF NOT EXISTS timezone TEXT NOT NULL DEFAULT 'Europe/Moscow';

ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_content_kind_check;
ALTER TABLE posts
    ADD CONSTRAINT posts_content_kind_check
    CHECK (content_kind IN ('post', 'story'));

ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_source_type_check;
ALTER TABLE posts
    ADD CONSTRAINT posts_source_type_check
    CHECK (source_type IN ('manual', 'grabber', 'rss', 'import', 'api'));

ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_moderation_status_check;
ALTER TABLE posts
    ADD CONSTRAINT posts_moderation_status_check
    CHECK (
        moderation_status IS NULL
        OR moderation_status IN (
            'pending_moderation', 'approved', 'rejected', 'suppressed_dedup'
        )
    );

ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_story_ttl_check;
ALTER TABLE posts
    ADD CONSTRAINT posts_story_ttl_check
    CHECK (
        story_ttl IS NULL
        OR story_ttl IN ('ttl_1h', 'ttl_12h', 'ttl_24h', 'ttl_48h')
    );

-- Align status enum with OpenAPI ApiEnums.PostStatus (deleted_from_network).
ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_status_check;
ALTER TABLE posts
    ADD CONSTRAINT posts_status_check CHECK (status IN (
        'draft', 'pending_approval', 'approved', 'scheduled', 'publishing',
        'published', 'partially_published', 'failed', 'paused', 'archived',
        'deleted_from_network'
    ));

-- ---------------------------------------------------------------------------
-- publications: waiting_for_channel (channel reauth / missing rights)
-- ---------------------------------------------------------------------------
ALTER TABLE publications DROP CONSTRAINT IF EXISTS publications_state_check;
ALTER TABLE publications
    ADD CONSTRAINT publications_state_check CHECK (state IN (
        'pending', 'queued', 'in_progress', 'succeeded',
        'failed_retryable', 'failed_permanent', 'cancelled', 'waiting_for_channel'
    ));

-- ---------------------------------------------------------------------------
-- Calendar / list indexes (100k users ×3): avoid sequential scans + N+1 joins
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS posts_calendar_ws_sched_idx
    ON posts (workspace_id, scheduled_at)
    WHERE deleted_at IS NULL AND scheduled_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS posts_calendar_ws_status_sched_idx
    ON posts (workspace_id, status, scheduled_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS posts_ws_author_sched_idx
    ON posts (workspace_id, author_id, scheduled_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS posts_ws_project_sched_idx
    ON posts (workspace_id, project_id, scheduled_at)
    WHERE deleted_at IS NULL AND project_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS posts_ws_content_kind_sched_idx
    ON posts (workspace_id, content_kind, scheduled_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS posts_ws_source_type_idx
    ON posts (workspace_id, source_type)
    WHERE deleted_at IS NULL AND source_type <> 'manual';

CREATE INDEX IF NOT EXISTS posts_ws_updated_idx
    ON posts (workspace_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS posts_ws_title_idx
    ON posts (workspace_id, title)
    WHERE deleted_at IS NULL AND title IS NOT NULL;

-- Variant lookups for calendar network/channel filters without N+1
CREATE INDEX IF NOT EXISTS post_variants_post_network_idx
    ON post_variants (post_id, network);

CREATE INDEX IF NOT EXISTS post_variants_channel_post_idx
    ON post_variants (channel_id, post_id);

-- Publication list / attempts by workspace+time (calendar-adjacent)
CREATE INDEX IF NOT EXISTS publications_ws_run_at_idx
    ON publications (workspace_id, run_at);

CREATE INDEX IF NOT EXISTS publications_ws_state_run_at_idx
    ON publications (workspace_id, state, run_at);

-- GIN for media_id presence checks inside base_content / variant content
CREATE INDEX IF NOT EXISTS posts_base_content_gin_idx
    ON posts USING GIN (base_content jsonb_path_ops);

CREATE INDEX IF NOT EXISTS post_variants_content_gin_idx
    ON post_variants USING GIN (content jsonb_path_ops);
