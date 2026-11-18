-- BE-10: publication pipeline prod-quality extensions
-- * scheduled_actions kinds for stories (BE-16 hook) and pin (immediate/scheduled)
-- * extra partitions so long-running envs stay ahead of run_at horizon

-- Expand kind check (squawk-safe: drop/re-add NOT VALID then VALIDATE)
ALTER TABLE scheduled_actions DROP CONSTRAINT IF EXISTS scheduled_actions_kind_check;
ALTER TABLE scheduled_actions
    ADD CONSTRAINT scheduled_actions_kind_check
    CHECK (kind IN ('delete', 'unpin', 'first_comment', 'edit', 'pin', 'delete_story'))
    NOT VALID;
ALTER TABLE scheduled_actions VALIDATE CONSTRAINT scheduled_actions_kind_check;

-- Forward partitions for publications / scheduled_actions (Apr–Sep 2027)
CREATE TABLE IF NOT EXISTS publications_2027_04 PARTITION OF publications
    FOR VALUES FROM ('2027-04-01+00') TO ('2027-05-01+00');
CREATE TABLE IF NOT EXISTS publications_2027_05 PARTITION OF publications
    FOR VALUES FROM ('2027-05-01+00') TO ('2027-06-01+00');
CREATE TABLE IF NOT EXISTS publications_2027_06 PARTITION OF publications
    FOR VALUES FROM ('2027-06-01+00') TO ('2027-07-01+00');
CREATE TABLE IF NOT EXISTS publications_2027_07 PARTITION OF publications
    FOR VALUES FROM ('2027-07-01+00') TO ('2027-08-01+00');
CREATE TABLE IF NOT EXISTS publications_2027_08 PARTITION OF publications
    FOR VALUES FROM ('2027-08-01+00') TO ('2027-09-01+00');
CREATE TABLE IF NOT EXISTS publications_2027_09 PARTITION OF publications
    FOR VALUES FROM ('2027-09-01+00') TO ('2027-10-01+00');

CREATE TABLE IF NOT EXISTS scheduled_actions_2027_04 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-04-01+00') TO ('2027-05-01+00');
CREATE TABLE IF NOT EXISTS scheduled_actions_2027_05 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-05-01+00') TO ('2027-06-01+00');
CREATE TABLE IF NOT EXISTS scheduled_actions_2027_06 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-06-01+00') TO ('2027-07-01+00');
CREATE TABLE IF NOT EXISTS scheduled_actions_2027_07 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-07-01+00') TO ('2027-08-01+00');
CREATE TABLE IF NOT EXISTS scheduled_actions_2027_08 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-08-01+00') TO ('2027-09-01+00');
CREATE TABLE IF NOT EXISTS scheduled_actions_2027_09 PARTITION OF scheduled_actions
    FOR VALUES FROM ('2027-09-01+00') TO ('2027-10-01+00');
