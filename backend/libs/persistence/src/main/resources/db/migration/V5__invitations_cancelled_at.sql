-- Expand: soft-cancel for workspace invitations (TC-WS-06 revoked invite).
ALTER TABLE invitations
    ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE INDEX invitations_workspace_pending_idx
    ON invitations (workspace_id)
    WHERE accepted_at IS NULL AND cancelled_at IS NULL;
