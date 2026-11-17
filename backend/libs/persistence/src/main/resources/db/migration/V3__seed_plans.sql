-- Seed MVP plans from docs/product/billing-rules.md (stable UUIDv7-shaped ids).
INSERT INTO plans (id, code, name, price_month, price_year, limits)
VALUES
    (
        '019a0000-0001-7000-8000-000000000001',
        'free',
        'Free',
        0,
        0,
        '{"channels": 3, "seats": 1, "posts_month": 30, "ai_credits": 20}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000002',
        'start',
        'Старт',
        390,
        3744,
        '{"channels": 10, "seats": 1, "posts_month": null, "ai_credits": 200}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000003',
        'pro',
        'Про',
        990,
        9504,
        '{"channels": 30, "seats": 3, "posts_month": null, "ai_credits": 1000}'::jsonb
    ),
    (
        '019a0000-0001-7000-8000-000000000004',
        'agency',
        'Агентство',
        2990,
        28704,
        '{"channels": 100, "seats": 10, "posts_month": null, "ai_credits": 5000}'::jsonb
    )
ON CONFLICT (code) DO NOTHING;
