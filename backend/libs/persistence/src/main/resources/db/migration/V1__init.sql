-- Baseline schema for Posts (DEV-1). Full domain schema lands in BE-2.
CREATE TABLE IF NOT EXISTS schema_info (
    id          UUID PRIMARY KEY,
    version     TEXT NOT NULL,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_info (id, version)
VALUES ('018f0000-0000-7000-8000-000000000001', '0.1.0')
ON CONFLICT (id) DO NOTHING;
