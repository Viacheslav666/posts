ALTER TABLE social_accounts
    ADD COLUMN IF NOT EXISTS device_id TEXT;