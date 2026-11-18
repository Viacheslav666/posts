-- BE-28: Telegram topics + optional custom bot tokens (TG/Max). Expand-only.

ALTER TABLE channels
    ADD COLUMN IF NOT EXISTS thread_id TEXT;

ALTER TABLE channels
    ADD COLUMN IF NOT EXISTS bot_token_enc BYTEA;

COMMENT ON COLUMN channels.thread_id IS 'Telegram forum topic message_thread_id (nullable)';
COMMENT ON COLUMN channels.bot_token_enc IS 'Optional custom TG/Max bot token sealed with workspace DEK (ADR-0006)';
