-- Un compte ne peut appartenir qu'à une seule organisation vivante.
-- L'invariant est applicatif ET garanti par PostgreSQL.

CREATE UNIQUE INDEX ux_org_membership_account_alive
    ON organization_membership (account_id)
    WHERE deleted_at IS NULL;
