-- =============================================================================
-- V3 — ajoute staff_invitation (invitation par email, compte cible pas forcément
-- existant, Phase 2 tranche 5). À répercuter dans apontaja-schema.sql.
-- =============================================================================

CREATE TABLE staff_invitation (
    id              uuid PRIMARY KEY,
    salon_id        uuid NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    email           citext NOT NULL,
    role            text NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'EMPLOYEE')),
    invited_by      uuid NULL REFERENCES account(id) ON DELETE SET NULL,
    token_hash      text NOT NULL,
    expires_at      timestamptz NOT NULL,
    accepted_at     timestamptz NULL,
    revoked_at      timestamptz NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_staff_invitation_hash ON staff_invitation (token_hash);

-- Une seule invitation "en attente" à la fois par (salon, email) — évite le spam
-- d'invitations dupliquées ; une invitation acceptée ou révoquée libère la paire.
CREATE UNIQUE INDEX ux_staff_invitation_pending_alive
    ON staff_invitation (salon_id, email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_staff_invitation_salon
    ON staff_invitation (salon_id)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
