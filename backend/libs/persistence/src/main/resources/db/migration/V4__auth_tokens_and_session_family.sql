-- BE-5: auth tokens, TOTP backup codes, OAuth PKCE/link state, session family for refresh reuse detection.

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS family_id UUID;

UPDATE sessions
SET family_id = id
WHERE family_id IS NULL;

ALTER TABLE sessions
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS sessions_family_id_idx ON sessions (family_id);

CREATE TABLE auth_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    purpose     TEXT NOT NULL,
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT auth_tokens_purpose_check
        CHECK (purpose IN ('email_verify', 'password_reset')),
    CONSTRAINT auth_tokens_token_hash_uq UNIQUE (token_hash)
);

CREATE INDEX auth_tokens_user_id_idx ON auth_tokens (user_id);

CREATE TABLE totp_backup_codes (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    code_hash   TEXT NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX totp_backup_codes_user_id_idx ON totp_backup_codes (user_id);

CREATE TABLE oauth_states (
    id             UUID PRIMARY KEY,
    provider       TEXT NOT NULL,
    state          TEXT NOT NULL,
    code_verifier  TEXT NOT NULL,
    nonce          TEXT,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT oauth_states_provider_check
        CHECK (provider IN ('vk', 'yandex')),
    CONSTRAINT oauth_states_state_uq UNIQUE (state)
);

CREATE TABLE oauth_link_challenges (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users (id),
    provider           TEXT NOT NULL,
    provider_user_id   TEXT NOT NULL,
    profile            JSONB NOT NULL DEFAULT '{}'::jsonb,
    token_hash         TEXT NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    consumed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT oauth_link_challenges_provider_check
        CHECK (provider IN ('vk', 'yandex')),
    CONSTRAINT oauth_link_challenges_token_hash_uq UNIQUE (token_hash)
);

CREATE INDEX oauth_link_challenges_user_id_idx ON oauth_link_challenges (user_id);
