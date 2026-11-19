-- BE-32: shared Telegram album buffer so media-group fragments survive across ingest replicas.
-- Flush is by silence (no new fragment for 3s), not by the first replica to see a partial group.

CREATE TABLE telegram_album_fragments (
    channel_id      UUID NOT NULL,
    media_group_id  TEXT NOT NULL,
    bot_id          UUID NOT NULL,
    update_id       BIGINT NOT NULL,
    payload         JSONB NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (channel_id, media_group_id, update_id)
);

CREATE INDEX telegram_album_fragments_received_idx
    ON telegram_album_fragments (received_at);

COMMENT ON TABLE telegram_album_fragments IS
    'In-flight Telegram media_group fragments; drained after 3s of silence (BE-32)';
