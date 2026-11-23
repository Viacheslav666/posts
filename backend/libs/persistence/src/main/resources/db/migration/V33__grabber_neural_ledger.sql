-- Grabber AI rewrite charges are recorded as kind 'neural' («Нейросеть»).
-- Expand-only: add the wider check, then drop the V2 check.

ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_kind_check_v33
    CHECK (kind IN ('charge', 'payment', 'refund', 'promo', 'referral', 'neural'))
    NOT VALID;
ALTER TABLE ledger_entries VALIDATE CONSTRAINT ledger_entries_kind_check_v33;
-- squawk-ignore ban-drop-constraint
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_kind_check;
