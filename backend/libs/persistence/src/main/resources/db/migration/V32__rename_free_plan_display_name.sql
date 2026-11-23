-- User-facing display name for the free tier (code remains `free`).
UPDATE plans
SET name = 'Попробуй'
WHERE code = 'free';