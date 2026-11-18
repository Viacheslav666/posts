-- BE-23: cache of media uploaded to a social channel (sha256+channel reuse).
-- Expand-only: new table + constraints via NOT VALID + VALIDATE.

CREATE TABLE channel_media_cache (
    id              UUID PRIMARY KEY,
    channel_id      UUID NOT NULL REFERENCES channels (id),
    media_asset_id  UUID NOT NULL REFERENCES media_assets (id),
    remote_ref      TEXT NOT NULL,
    remote_kind     TEXT NOT NULL DEFAULT 'photo',
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX channel_media_cache_channel_asset_uq
    ON channel_media_cache (channel_id, media_asset_id);

CREATE INDEX channel_media_cache_expires_at_idx
    ON channel_media_cache (expires_at)
    WHERE expires_at IS NOT NULL;

ALTER TABLE channel_media_cache
    ADD CONSTRAINT channel_media_cache_remote_ref_not_blank
    CHECK (char_length(btrim(remote_ref)) > 0)
    NOT VALID;
ALTER TABLE channel_media_cache VALIDATE CONSTRAINT channel_media_cache_remote_ref_not_blank;

ALTER TABLE channel_media_cache
    ADD CONSTRAINT channel_media_cache_remote_kind_check
    CHECK (remote_kind IN ('photo', 'video', 'doc', 'audio', 'clip', 'other'))
    NOT VALID;
ALTER TABLE channel_media_cache VALIDATE CONSTRAINT channel_media_cache_remote_kind_check;
