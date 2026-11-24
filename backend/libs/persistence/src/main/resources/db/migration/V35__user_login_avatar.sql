-- Account icon: remember how the session signed in, and backfill social avatars
-- already stored on user_identities.profile (VK ID user_info "avatar" / photo_*).

ALTER TABLE sessions
    ADD COLUMN login_provider TEXT;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_login_provider_check
        CHECK (login_provider IS NULL OR login_provider IN ('vk', 'email', 'yandex', 'telegram'))
        NOT VALID;

ALTER TABLE sessions
    VALIDATE CONSTRAINT sessions_login_provider_check;

UPDATE users AS u
SET avatar_url = src.photo,
    updated_at = now()
FROM (
    SELECT DISTINCT ON (i.user_id)
        i.user_id,
        COALESCE(
            NULLIF(i.profile ->> 'avatar', ''),
            NULLIF(i.profile ->> 'photo_200', ''),
            NULLIF(i.profile ->> 'photo_max_orig', ''),
            NULLIF(i.profile ->> 'photo_max', ''),
            NULLIF(i.profile ->> 'photo_100', ''),
            NULLIF(i.profile ->> 'photo', '')
        ) AS photo
    FROM user_identities AS i
    WHERE i.provider IN ('vk', 'yandex', 'telegram')
    ORDER BY i.user_id, i.created_at ASC
) AS src
WHERE u.id = src.user_id
  AND u.avatar_url IS NULL
  AND src.photo IS NOT NULL
  AND (src.photo LIKE 'https://%' OR src.photo LIKE 'http://%');
