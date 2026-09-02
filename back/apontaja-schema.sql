-- =============================================================================
-- Apontaja — Schéma relationnel PostgreSQL de référence
-- =============================================================================
-- ⚠️  Ce fichier est la référence canonique (§4 du fichier de contexte). Il est dupliqué
-- verbatim dans src/main/resources/db/migration/V1__initial_schema.sql (Flyway impose ce nom
-- de fichier, impossible de le partager par référence) — toute modification du schéma doit
-- passer par ICI d'abord, puis se répercuter dans la copie Flyway.
-- =============================================================================
-- Conventions générales :
--   * PK en UUID (v7), GÉNÉRÉES CÔTÉ APPLICATION (Java), pas par PostgreSQL —
--     le cœur de Postgres (jusqu'à la 17) ne génère pas nativement de l'UUIDv7 ;
--     utiliser une lib type `uuid-creator` côté Spring plutôt qu'une extension DB.
--   * Toutes les dates/heures "instant" en `timestamptz`, toujours écrites/lues en UTC
--     depuis l'application (mapping Java `Instant`). Jamais de `timestamp` nu.
--   * Toutes les tables métier ont `created_at timestamptz NOT NULL DEFAULT now()`.
--   * Le soft-delete se fait via `deleted_at timestamptz NULL` ; une ligne est "vivante"
--     si `deleted_at IS NULL`. Les contraintes UNIQUE qui doivent survivre à un
--     soft-delete sont des INDEX UNIQUES PARTIELS `WHERE deleted_at IS NULL`.
--   * snake_case partout, noms de table au singulier.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS citext;      -- email insensible à la casse
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- contraintes d'exclusion (anti double-booking)

-- =============================================================================
-- ACCOUNT — identité technique pure
-- =============================================================================
CREATE TABLE account (
    id                  uuid PRIMARY KEY,
    email               citext NOT NULL,
    email_verified_at   timestamptz NULL,
    password_hash       text NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz NULL
);

-- Un email ne peut être utilisé que par un seul compte vivant à la fois ;
-- un email libéré par un compte supprimé (soft-delete) redevient utilisable.
CREATE UNIQUE INDEX ux_account_email_alive
    ON account (email)
    WHERE deleted_at IS NULL;

-- =============================================================================
-- REFRESH_TOKEN — session, un par device
-- =============================================================================
CREATE TABLE refresh_token (
    id              uuid PRIMARY KEY,
    account_id      uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    token_hash      text NOT NULL,
    device_info     text NULL,
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz NULL,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX ix_refresh_token_account ON refresh_token (account_id) WHERE revoked_at IS NULL;

-- =============================================================================
-- CONSENT_RECORD — consentement plateforme (CGU, confidentialité, marketing global)
-- =============================================================================
CREATE TABLE consent_record (
    id              uuid PRIMARY KEY,
    account_id      uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    type            text NOT NULL CHECK (type IN ('TOS', 'PRIVACY', 'MARKETING_PLATFORM')),
    version         text NOT NULL,
    accepted_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_consent_record_account_type ON consent_record (account_id, type);

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

CREATE UNIQUE INDEX ux_account_token_hash ON account_token (token_hash);
CREATE INDEX ix_account_token_account_type
    ON account_token (account_id, type)
    WHERE used_at IS NULL;

-- =============================================================================
-- ORGANIZATION — regroupement "société" au-dessus des salons
-- =============================================================================
CREATE TABLE organization (
    id              uuid PRIMARY KEY,
    name            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE TABLE organization_membership (
    id                  uuid PRIMARY KEY,
    account_id          uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    organization_id     uuid NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    role                text NOT NULL CHECK (role IN ('OWNER')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz NULL
);

CREATE UNIQUE INDEX ux_org_membership_alive
    ON organization_membership (account_id, organization_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_org_membership_org ON organization_membership (organization_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- SALON
-- =============================================================================
CREATE TABLE salon (
    id                  uuid PRIMARY KEY,
    organization_id     uuid NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name                text NOT NULL,
    address             text NOT NULL,
    postal_code         text NOT NULL,
    city                text NOT NULL,
    country             text NOT NULL,
    latitude            numeric(9,6) NULL,
    longitude           numeric(9,6) NULL,
    phone               text NULL,
    timezone            text NOT NULL,  -- identifiant IANA, ex: 'Europe/Lisbon'
    created_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz NULL
);

CREATE INDEX ix_salon_organization ON salon (organization_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- STAFF_MEMBERSHIP — rattachement d'un compte à un salon avec un rôle
-- =============================================================================
CREATE TABLE staff_membership (
    id              uuid PRIMARY KEY,
    account_id      uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    salon_id        uuid NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    role            text NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'EMPLOYEE')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE UNIQUE INDEX ux_staff_membership_alive
    ON staff_membership (account_id, salon_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_staff_membership_salon ON staff_membership (salon_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- RESOURCE — employé ou équipement réservable dans le planning
-- =============================================================================
CREATE TABLE resource (
    id                      uuid PRIMARY KEY,
    salon_id                uuid NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    type                    text NOT NULL CHECK (type IN ('EMPLOYEE', 'MACHINE')),
    name                    text NOT NULL,
    staff_membership_id     uuid NULL REFERENCES staff_membership(id) ON DELETE SET NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    deleted_at              timestamptz NULL
);

CREATE UNIQUE INDEX ux_resource_salon_name_alive
    ON resource (salon_id, name)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_resource_salon ON resource (salon_id) WHERE deleted_at IS NULL;

-- Garantie applicative obligatoire (non exprimable en simple FK/CHECK Postgres) :
-- resource.staff_membership_id, s'il est renseigné, doit référencer un
-- staff_membership dont le salon_id est identique à resource.salon_id.
-- => Renforcer par un trigger BEFORE INSERT/UPDATE si l'incohérence s'avère
--    possible en pratique (v1 : contrôle service applicatif suffisant, écrire
--    un test d'intégration dédié).

-- =============================================================================
-- SERVICE — prestation proposée par le salon
-- =============================================================================
CREATE TABLE service (
    id                          uuid PRIMARY KEY,
    salon_id                    uuid NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    name                        text NOT NULL,
    description                 text NULL,
    default_duration_minutes    integer NOT NULL CHECK (default_duration_minutes > 0 AND default_duration_minutes <= 1440),
    default_price_cents         integer NOT NULL CHECK (default_price_cents >= 0),
    created_at                  timestamptz NOT NULL DEFAULT now(),
    deleted_at                  timestamptz NULL
);

CREATE UNIQUE INDEX ux_service_salon_name_alive
    ON service (salon_id, name)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_service_salon ON service (salon_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- SERVICE_RESOURCE — ressources capables de réaliser un service
-- =============================================================================
CREATE TABLE service_resource (
    service_id                  uuid NOT NULL REFERENCES service(id) ON DELETE CASCADE,
    resource_id                 uuid NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    override_price_cents        integer NULL CHECK (override_price_cents >= 0),
    override_duration_minutes   integer NULL CHECK (override_duration_minutes > 0 AND override_duration_minutes <= 1440),
    PRIMARY KEY (service_id, resource_id)
);

-- Garantie applicative obligatoire : resource.salon_id doit être égal à
-- service.salon_id pour toute ligne de service_resource. Contrôle service
-- applicatif en v1 ; envisager un trigger si des écritures directes SQL
-- (scripts d'admin, migrations de données) contournent la couche service.

-- =============================================================================
-- SCHEDULE — horaires récurrents (salon OU ressource, jamais les deux)
-- =============================================================================
CREATE TABLE schedule (
    id              uuid PRIMARY KEY,
    salon_id        uuid NULL REFERENCES salon(id) ON DELETE CASCADE,
    resource_id     uuid NULL REFERENCES resource(id) ON DELETE CASCADE,
    day_of_week     smallint NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0 = dimanche
    start_time      time NOT NULL,
    end_time        time NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_schedule_owner_xor CHECK (
        (salon_id IS NOT NULL AND resource_id IS NULL)
        OR
        (salon_id IS NULL AND resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_schedule_time_order CHECK (end_time > start_time)
);

CREATE INDEX ix_schedule_salon ON schedule (salon_id, day_of_week) WHERE salon_id IS NOT NULL;
CREATE INDEX ix_schedule_resource ON schedule (resource_id, day_of_week) WHERE resource_id IS NOT NULL;

-- Rappel de règle métier (non applicable en SQL pur sans complexité disproportionnée
-- pour la v1) : une Schedule de type ressource doit toujours être interprétée à
-- travers Resource -> Salon pour déterminer son périmètre réel ; ne jamais l'utiliser
-- seule pour un contrôle d'autorisation par salon.
--
-- Amélioration possible plus tard : contrainte EXCLUDE anti-chevauchement par
-- (owner, day_of_week) via btree_gist sur un type range construit à partir de
-- start_time/end_time — non retenue en v1, le contrôle applicatif + tests
-- unitaires exhaustifs (Phase 3) suffisent pour démarrer.

-- =============================================================================
-- CLOSURE — fermeture exceptionnelle (salon OU ressource, jamais les deux)
-- =============================================================================
CREATE TABLE closure (
    id              uuid PRIMARY KEY,
    salon_id        uuid NULL REFERENCES salon(id) ON DELETE CASCADE,
    resource_id     uuid NULL REFERENCES resource(id) ON DELETE CASCADE,
    start_at        timestamptz NOT NULL,
    end_at          timestamptz NOT NULL,
    reason          text NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_closure_owner_xor CHECK (
        (salon_id IS NOT NULL AND resource_id IS NULL)
        OR
        (salon_id IS NULL AND resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_closure_time_order CHECK (end_at > start_at)
);

CREATE INDEX ix_closure_salon ON closure (salon_id) WHERE salon_id IS NOT NULL;
CREATE INDEX ix_closure_resource ON closure (resource_id) WHERE resource_id IS NOT NULL;

-- =============================================================================
-- CUSTOMER_PROFILE — identité personne, "réclamable" par un compte
-- =============================================================================
CREATE TABLE customer_profile (
    id              uuid PRIMARY KEY,
    account_id      uuid NULL REFERENCES account(id) ON DELETE SET NULL,
    first_name      text NOT NULL,
    last_name       text NOT NULL,
    email           citext NULL,
    phone           text NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL,

    CONSTRAINT ck_customer_profile_has_contact CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

-- Un compte ne "réclame" qu'un seul profil client (le profil canonique).
CREATE UNIQUE INDEX ux_customer_profile_account_alive
    ON customer_profile (account_id)
    WHERE account_id IS NOT NULL AND deleted_at IS NULL;

-- Index de recherche pour le matching "profil déjà réclamé" décrit dans le
-- modèle (PAS une contrainte unique : plusieurs profils manuels non réclamés
-- peuvent légitimement partager un email selon la règle de matching actée).
CREATE INDEX ix_customer_profile_email ON customer_profile (email) WHERE deleted_at IS NULL;

-- =============================================================================
-- SALON_CUSTOMER_LINK — carnet client, données strictement propres au salon
-- =============================================================================
CREATE TABLE salon_customer_link (
    id                              uuid PRIMARY KEY,
    salon_id                        uuid NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    customer_profile_id             uuid NOT NULL REFERENCES customer_profile(id) ON DELETE CASCADE,
    source                          text NOT NULL CHECK (source IN ('MANUAL', 'SELF')),
    internal_notes                  text NULL,
    marketing_consent_at            timestamptz NULL,
    marketing_consent_revoked_at    timestamptz NULL,
    first_visit_at                  timestamptz NULL,
    last_visit_at                   timestamptz NULL,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    deleted_at                      timestamptz NULL
);

CREATE UNIQUE INDEX ux_salon_customer_link_alive
    ON salon_customer_link (salon_id, customer_profile_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_salon_customer_link_profile ON salon_customer_link (customer_profile_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- APPOINTMENT
-- =============================================================================
CREATE TABLE appointment (
    id                              uuid PRIMARY KEY,
    salon_id                        uuid NOT NULL REFERENCES salon(id) ON DELETE RESTRICT,
    customer_profile_id             uuid NOT NULL REFERENCES customer_profile(id) ON DELETE RESTRICT,
    service_id                      uuid NOT NULL REFERENCES service(id) ON DELETE RESTRICT,
    start_at                        timestamptz NOT NULL,
    end_at                          timestamptz NOT NULL,
    status                          text NOT NULL CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    price_at_booking_cents          integer NOT NULL CHECK (price_at_booking_cents >= 0),
    duration_at_booking_minutes     integer NOT NULL CHECK (duration_at_booking_minutes > 0),
    cancelled_at                    timestamptz NULL,
    cancelled_by                    uuid NULL REFERENCES account(id) ON DELETE SET NULL,
    cancellation_reason             text NULL,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    deleted_at                      timestamptz NULL,

    CONSTRAINT ck_appointment_time_order CHECK (end_at > start_at)
);

-- ON DELETE RESTRICT volontaire sur salon/customer_profile/service : on ne
-- supprime jamais physiquement une entité qui a des rendez-vous rattachés,
-- le soft-delete est la seule voie (corrige le bug "500 brut sur suppression"
-- de l'ancien projet — l'application doit intercepter ce cas et renvoyer un
-- message métier clair plutôt que de laisser fuiter l'erreur SQL).

CREATE INDEX ix_appointment_salon_start ON appointment (salon_id, start_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_appointment_customer ON appointment (customer_profile_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_appointment_service ON appointment (service_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- APPOINTMENT_RESOURCE — ressources engagées sur un RDV (multi-ressource dès la
-- conception, v1 = toujours une seule ligne par appointment)
-- =============================================================================
CREATE TABLE appointment_resource (
    appointment_id      uuid NOT NULL REFERENCES appointment(id) ON DELETE CASCADE,
    resource_id         uuid NOT NULL REFERENCES resource(id) ON DELETE RESTRICT,

    -- Colonnes dénormalisées, synchronisées par trigger depuis `appointment`
    -- (voir ci-dessous). Existent uniquement pour permettre la contrainte
    -- d'exclusion anti-chevauchement au niveau base de données.
    starts_at            timestamptz NOT NULL,
    ends_at               timestamptz NOT NULL,
    is_active            boolean NOT NULL DEFAULT true,

    PRIMARY KEY (appointment_id, resource_id)
);

CREATE INDEX ix_appointment_resource_resource ON appointment_resource (resource_id);

-- Garantie applicative obligatoire : resource.salon_id doit être égal à
-- appointment.salon_id pour toute ligne d'appointment_resource. Contrôle
-- service applicatif en v1 (validé par test d'intégration dédié) ; peut être
-- durci par trigger si nécessaire.

-- --- Garantie DB anti double-booking (ferme définitivement le bug hérité) ---
-- Une même ressource ne peut avoir deux plages actives qui se chevauchent.
ALTER TABLE appointment_resource
    ADD COLUMN during tstzrange
    GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED;

ALTER TABLE appointment_resource
    ADD CONSTRAINT ex_appointment_resource_no_overlap
    EXCLUDE USING gist (resource_id WITH =, during WITH &&)
    WHERE (is_active);

-- Triggers de synchronisation : maintiennent starts_at/ends_at/is_active en
-- phase avec l'appointment parent, pour que la contrainte d'exclusion
-- ci-dessus reste toujours exacte sans dépendre de la discipline applicative.

CREATE OR REPLACE FUNCTION fn_appointment_resource_sync_on_insert()
RETURNS trigger AS $$
BEGIN
    SELECT a.start_at, a.end_at, (a.status <> 'CANCELLED' AND a.deleted_at IS NULL)
    INTO NEW.starts_at, NEW.ends_at, NEW.is_active
    FROM appointment a
    WHERE a.id = NEW.appointment_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_appointment_resource_sync_on_insert
    BEFORE INSERT ON appointment_resource
    FOR EACH ROW EXECUTE FUNCTION fn_appointment_resource_sync_on_insert();

CREATE OR REPLACE FUNCTION fn_appointment_propagate_to_resources()
RETURNS trigger AS $$
BEGIN
    UPDATE appointment_resource
    SET starts_at = NEW.start_at,
        ends_at   = NEW.end_at,
        is_active = (NEW.status <> 'CANCELLED' AND NEW.deleted_at IS NULL)
    WHERE appointment_id = NEW.id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_appointment_propagate_to_resources
    AFTER UPDATE OF start_at, end_at, status, deleted_at ON appointment
    FOR EACH ROW EXECUTE FUNCTION fn_appointment_propagate_to_resources();

-- Note d'implémentation (à documenter dans la Phase 3 avec le vertical slice
-- appointment) : cette contrainte d'exclusion est une DERNIÈRE LIGNE DE
-- DÉFENSE au niveau du stockage. Le contrôle métier applicatif (créneau
-- dans les horaires d'ouverture, disponibilité ressource, fermetures
-- exceptionnelles) reste nécessaire en amont pour produire une erreur
-- métier propre (400) plutôt que de laisser remonter une violation de
-- contrainte SQL brute (23P01) au client — l'ExceptionListener/handler
-- global doit explicitement mapper cette violation vers un message clair.

-- =============================================================================
-- AUDIT_LOG — traçabilité (fusions, suppressions, changements de rôle...)
-- =============================================================================
CREATE TABLE audit_log (
    id                      uuid PRIMARY KEY,
    actor_account_id        uuid NULL REFERENCES account(id) ON DELETE SET NULL,
    action                  text NOT NULL,
    entity_type             text NOT NULL,
    entity_id               uuid NOT NULL,
    before                  jsonb NULL,   -- whitelist de champs uniquement, jamais de PII brute
    after                   jsonb NULL,   -- idem
    created_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_account_id);
CREATE INDEX ix_audit_log_created_at ON audit_log (created_at);

-- Politique de rétention à définir en Phase 5 (durcissement RGPD) — voir
-- APONTAJA-RESTART-CONTEXT.md. Ne pas ajouter de purge automatique tant que
-- cette politique n'est pas explicitement actée.

-- =============================================================================
-- FIN DU SCHÉMA v1
-- =============================================================================
