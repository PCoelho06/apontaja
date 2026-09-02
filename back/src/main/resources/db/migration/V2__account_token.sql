-- =============================================================================
-- V2 — ajoute account_token (vérification email + reset mot de passe, tranche 7).
-- À répercuter dans apontaja-schema.sql (référence canonique) juste après la
-- section CONSENT_RECORD — même règle de duplication que pour V1 (voir l'en-tête
-- de V1__initial_schema.sql).
-- =============================================================================

-- =============================================================================
-- ACCOUNT_TOKEN — tokens à usage unique (vérification email, reset mot de passe)
-- =============================================================================
CREATE TABLE account_token (
    id              uuid PRIMARY KEY,
    account_id      uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    type            text NOT NULL CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    token_hash      text NOT NULL,
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- Un seul type discriminé plutôt que deux tables quasi identiques — même
-- logique que CONSENT_RECORD (type TOS/PRIVACY/MARKETING_PLATFORM).
CREATE UNIQUE INDEX ux_account_token_hash ON account_token (token_hash);
CREATE INDEX ix_account_token_account_type
    ON account_token (account_id, type)
    WHERE used_at IS NULL;
