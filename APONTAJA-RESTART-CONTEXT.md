# Apontaja — Reboot du projet (anciennement "Marquei", Symfony/Vue → Spring Boot/Vue)

> **Comment utiliser ce fichier** : à chaque nouvelle session avec Claude, colle ce fichier
> (ou upload-le) et demande de continuer le travail à partir de la section "État d'avancement".
> À la fin de chaque session, demande explicitement à Claude de **mettre à jour ce fichier**
> puis retélécharge-le pour la prochaine fois.
>
> **Principe important** : on ne réinjecte plus le gitingest de l'ancien repo dans le contexte
> des sessions suivantes. L'audit initial a rempli son rôle ; ce fichier capture les *décisions*
> et les *enseignements*, pas le code source d'origine.
>
> **Statuts de décision** — chaque choix technique est marqué :
> - `[DECIDED]` — tranché, ne pas rouvrir sauf demande explicite
> - `[PROVISIONAL]` — direction retenue mais pas encore validée en profondeur (revue technique,
>   implémentation, ou confirmation utilisateur en attente) — à traiter avec prudence, peut
>   encore changer
> - `[OPEN]` — question non tranchée, à trancher avant que ça devienne bloquant

Dernière mise à jour : session 7 (schéma relationnel PostgreSQL complet produit, corrections
issues de la deuxième revue croisée). Prêt à démarrer concrètement la Phase 0.

---

## 1. Objectif du projet

Reprendre "Apontaja" (SaaS de gestion de rendez-vous pour salons — coiffure/beauté) en repartant
d'un nouveau repo, en conservant ce qui a de la valeur (modèle métier, écrans, UX globale) mais en :

- migrant le backend **Symfony (PHP) → Spring Boot (Java)**
- **durcissant la sécurité** (authentification, autorisation, gestion des secrets)
- ajoutant une **vraie stratégie de tests** (unitaires + intégration), absente aujourd'hui
- corrigeant les **bugs de logique métier** identifiés dans l'ancien code
- refondant en profondeur le **frontend**, désormais scindé en deux portails distincts
  (portail salon / portail client)
- la partie paiement (Stripe) est **repoussée à plus tard**

## 2. Décisions techniques

| Sujet | Décision | Statut |
|---|---|---|
| Backend | Spring Boot 3.x, Java 21 | `[DECIDED]` |
| Base de données | PostgreSQL | `[DECIDED]` |
| Migrations DB | Flyway | `[DECIDED]` |
| Frontend | Vue 3 + TypeScript + Pinia + Tailwind 4 | `[DECIDED]` |
| Structure repo | Mono-repo : `back/`, `portail-salon/`, `portail-client/` | `[DECIDED]` |
| Design system partagé entre portails | Partagé via un package `packages/ui-kit`, repart entièrement de zéro (pas de portage des composants `Coelho*` de l'ancien projet) — récupération ponctuelle d'éléments seulement si un besoin précis se présente | `[DECIDED]` |
| Structure de `back/` | Module unique **Maven** (choix entre Gradle/Maven laissé ouvert dans la version initiale, tranché en session : Maven). Package **par domaine métier** (`account`, `organization`, `salon`, `resource`, `service`, `appointment`, `customer`, `audit`), et **au sein de chaque domaine**, sous-packages par couche : `web` (controllers + DTO request/response), `application` (services/cas d'usage), `domain` (entités, value objects, règles métier), `infrastructure` (repositories JPA, adapters). DTO obligatoires à **toutes** les frontières : HTTP (web ↔ application) et inter-domaines (jamais un domaine n'expose ses entités JPA à un autre). Graphe de dépendances autorisées entre domaines (vérifié mécaniquement par `ArchitectureTest`, ArchUnit, depuis la Phase 0 étape 4) : `account` (racine, aucune dépendance) → `organization` → `salon` → `resource` → `service` ; `customer` → `account` ; `appointment` → `salon`, `resource`, `service`, `customer` (sommet du graphe, rien n'en dépend) ; `audit` est un utilitaire transverse que tous les domaines peuvent appeler en écriture, sans dépendre de personne. Règle de couches : `web` → `application` → `domain`, `infrastructure` → `domain` (implémente les ports définis par le domaine), jamais l'inverse. Migration vers multi-module possible plus tard si un vrai besoin apparaît | `[DECIDED]` |
| Backend — version | **Spring Boot 4.1** (Spring Framework 7), pas 3.x comme indiqué plus bas dans la table "Décision" initiale : la ligne 3.x est passée EOL le 30/06/2026, en cours de projet, tranché en session pour repartir directement sur la ligne actuelle. Java 21 inchangé (compatible) | `[DECIDED]` |
| Lint back (Java) | Checkstyle, ruleset custom (`back/checkstyle.xml`) volontairement resserré (pas de `google_checks.xml`/`sun_checks.xml` complets — conflit d'indentation avec `.editorconfig` et risque de faux positifs sur le module `Indentation`). Lié à la phase Maven `verify` | `[DECIDED]` |
| Hébergement Git / CI | GitHub (GitHub Actions). `.github/workflows/ci.yml` : job `back` (`mvn -B clean verify`, couvre build + tests + ArchUnit + Checkstyle) et job `front` (pnpm install/lint/build/test), sur chaque PR + push `main` + déclenchement manuel | `[DECIDED]` |
| Gestionnaire de workspace front | pnpm workspaces. Pas de Turborepo pour l'instant (introduit plus tard si le temps de build/test incrémental le justifie) | `[DECIDED]` |
| Auth — mécanisme | JWT access token courte durée + refresh token opaque, hashé en base, en cookie httpOnly + Secure + SameSite=Strict | `[DECIDED]` |
| Auth — access token côté front | En mémoire JS uniquement, jamais dans localStorage/sessionStorage | `[DECIDED]` |
| Auth — CSRF | `SameSite=Strict` + vérification d'un header custom sur les endpoints sensibles (`/auth/refresh` notamment) ; pas de système de token CSRF complet en v1 (API JSON pure, vecteur CSRF classique via formulaire HTML non applicable) | `[PROVISIONAL]` — dépend encore de l'architecture finale de déploiement front/back |
| Autorisation | Centralisée via `@PreAuthorize` / method security, jamais de checks ad hoc dispersés | `[DECIDED]` |
| Tests | JUnit5 + Testcontainers (PostgreSQL réel) côté back, Vitest (+ éventuellement Playwright) côté front, incluant une matrice de tests d'autorisation systématique | `[DECIDED]` |
| Paiement | Stripe conservé mais repoussé hors périmètre v1 | `[DECIDED]` |
| Clés primaires | UUID v7 partout côté entités exposées à l'API | `[DECIDED]` |
| Suppression | Soft-delete (`deletedAt`) + index uniques partiels PostgreSQL (`WHERE deleted_at IS NULL`) pour éviter qu'un élément supprimé bloque la réutilisation d'une valeur unique | `[DECIDED]` |
| RGPD | Compliance requise dès la conception | `[DECIDED]` |
| Audit log | Jamais de copie automatique de données personnelles/sensibles dans `before`/`after` ; whitelist explicite de champs traçables | `[DECIDED]` |
| Permissions | Rôle simple (OWNER/MANAGER/EMPLOYEE) suffisant pour la v1 | `[DECIDED]` |
| Organisation | Regroupement "société" au-dessus des salons, créé automatiquement même pour un salon unique. Règle explicite : l'appartenance à l'organisation (OWNER) donne une capacité **administrative** sur ses salons, mais **toute opération métier reste systématiquement scopée au salon** (jamais de requête/permission qui traverse l'organisation sans passer par le salon) | `[DECIDED]` |
| Horaires & fermetures polymorphiques | Rejet du pattern `ownerType`/`ownerId` (perte d'intégrité référentielle côté PostgreSQL) au profit de `salonId` nullable + `resourceId` nullable + `CHECK` garantissant qu'exactement un des deux est renseigné | `[DECIDED]` |
| Multi-ressource par service | Pas de besoin fonctionnel en v1, modèle conçu pour le supporter (table de jointure `AppointmentResource` avec contraintes dès maintenant) | `[DECIDED]` |
| Client multi-salons | Un compte client peut réserver dans plusieurs salons différents | `[DECIDED]` |
| Matching automatique `CustomerProfile` à l'inscription | Simplifié : **pas de fusion/consolidation rétroactive automatique** en v1 (trop risqué : faux positifs sur email partagé/erreur de saisie). On garde uniquement le sens simple : un salon créant un client manuellement dont l'email correspond à un `CustomerProfile` déjà "réclamé" (avec `accountId`) le relie directement. La fusion rétroactive de l'historique devient une fonctionnalité v2 explicite, pilotée par l'utilisateur | `[DECIDED]` |
| Employés & portail salon | Accès prévu pour gérer leur propre planning, mais pas prioritaire en v1 | `[DECIDED]` (report) |
| Stats client (visites, etc.) | Calculées à la volée en v1, pas de dénormalisation | `[DECIDED]` |
| Roadmap | Développement en tranches verticales (backend + frontend d'une fonctionnalité en même temps) plutôt que "tout le backend puis tout le frontend" | `[DECIDED]` |
| Gestion du temps | RDV : `TIMESTAMPTZ` en DB / `Instant` en Java. Horaires récurrents : `LocalTime` + `DayOfWeek` (09:00 n'est pas un instant). Affichage : conversion `Instant → Salon.timezone → ZonedDateTime` | `[DECIDED]` |

## 3. Audit du repo existant — résumé

### 3.1 Sécurité — Critique (P0)
- Mot de passe loggé en clair dans `confirmPassword`
- IDOR massif sur les rendez-vous (get/update/delete/create sans vérification d'appartenance)
- IDOR sur `getUserById` (accès à n'importe quel profil)
- Création de client sans vérification de propriété du salon
- Recherche publique de salons cassée (réutilise le filtre "salons de l'utilisateur connecté")
- Refresh tokens en clair en base, logout qui révoque le mauvais token
- Tokens en `localStorage` côté front
- Fuite d'infos via l'exception listener (messages bruts renvoyés au client)
- Regex de mot de passe buggée (intervalle de caractères non voulu, pas d'ancrage complet)
- CORS incohérent (override en dur sur localhost)
- Secret applicatif commité en clair

### 3.2 Sécurité — Important (P1)
- Pas de rate limiting, pas de vérification d'email, pas de "mot de passe oublié"
- Pas de plafond sur la pagination
- Entité `User` unique pour staff et clients (clients sans mot de passe)
- `getRoles()` toujours `['ROLE_USER']`, RBAC géré à la main de façon incohérente
- Pas de headers de sécurité (CSP, HSTS)

### 3.3 Logique métier
- Détection de double-booking incomplète (pas de borne de fin de journée, limite de 100 résultats)
- Aucune revalidation à la mise à jour d'un RDV
- Seule l'heure de début du RDV est vérifiée par rapport aux horaires (jamais la fin)
- Méthodes de repository appelées mais inexistantes (crash garanti)
- Syntaxe invalide + null pointer sur la récupération d'abonnement salon
- Annulation d'abonnement non transactionnelle (suppression locale avant appel Stripe)
- Pas de vérification de cohérence ressource ↔ salon ↔ service à la création d'un RDV
- Pas de contrôle anti-chevauchement sur les horaires enregistrés
- Suppression de ressource/salon avec RDV liés → 500 brut probable
- Incohérence DB entre environnements (MySQL dev / config Postgres prod)
- Dates en string plutôt qu'ISO-8601/UTC

### 3.4 Dette technique générale
- Zéro test (back et front)
- Auto-mapper générique par réflexion → risque de mass-assignment
- Composants dupliqués, fichiers morts
- `baseURL` API en dur
- Race condition sur le refresh token côté front
- Pas de CI/CD, pas de lint enforcé

---

## 4. Modèle de données

### Principe directeur

Séparer **identité technique** (`Account`), **rattachement staff** (par salon et par
organisation), et **identité client** (`CustomerProfile`, indépendante d'un compte).

### Règle de matching client `[DECIDED]`

- Un salon créant un client manuellement ne déclenche **jamais** de matching avec un profil créé
  manuellement par un **autre** salon.
- **Exception** : si le `CustomerProfile` trouvé a déjà un `accountId` (compte "réclamé"), le
  matching automatique est autorisé et on relie directement.
- **Pas de fusion/consolidation rétroactive automatique** à la création de compte en v1 (voir §2)
  — un nouveau `CustomerProfile` est simplement créé, lié au compte. La fusion de l'historique
  pré-existant est reportée à une fonctionnalité v2 explicite et pilotée par l'utilisateur.
- Cloisonnement strict : seules les données d'**identité** (nom/email/téléphone) sont partagées
  via le `CustomerProfile` commun. Les données propres à la relation (notes internes, consentement
  marketing, historique de visite) restent sur `SalonCustomerLink`, jamais visibles d'un autre
  salon. Les requêtes de RDV restent toujours scopées par `salonId`.

### Dictionnaire des entités

| Entité | Champs clés | Notes |
|---|---|---|
| `Account` | id (UUIDv7), email, emailVerifiedAt, passwordHash, createdAt, deletedAt | Identité technique pure |
| `Organization` | id, name, createdAt, deletedAt | Créée automatiquement à l'inscription. Donne une capacité **administrative** sur ses salons uniquement — jamais de bypass du scoping salon pour les opérations métier |
| `OrganizationMembership` | accountId, organizationId, role=OWNER | |
| `Salon` | id, organizationId, name, address, lat/lng, timezone, phone, createdAt, deletedAt | `timezone` explicite (IANA, ex: `Europe/Lisbon`) |
| `StaffMembership` | accountId, salonId, role (OWNER/MANAGER/EMPLOYEE), createdAt, deletedAt | "Cette personne travaille dans ce salon et a un rôle" |
| `CustomerProfile` | id, accountId (nullable), firstName, lastName, email, phone, createdAt, deletedAt | Identité personne, "réclamable" par un compte |
| `SalonCustomerLink` | id, salonId, customerProfileId, source (MANUAL/SELF), internalNotes, marketingConsentAt, marketingConsentRevokedAt, firstVisitAt, lastVisitAt, createdAt, deletedAt | Carnet client, strictement propre au salon. `UNIQUE(salonId, customerProfileId) WHERE deleted_at IS NULL` |
| `Resource` | id, salonId, type (EMPLOYEE/MACHINE), name, staffMembershipId (nullable, v2), createdAt, deletedAt | "Cette ressource peut être réservée dans le planning" — distinct de `StaffMembership`, lié optionnellement |
| `Service` | id, salonId, name, description, defaultDuration, defaultPrice, createdAt, deletedAt | |
| `ServiceResource` | serviceId, resourceId, overridePrice (nullable), overrideDuration (nullable) | |
| `Schedule` | id, salonId (nullable), resourceId (nullable), dayOfWeek, startTime, endTime | `CHECK ((salon_id IS NOT NULL AND resource_id IS NULL) OR (salon_id IS NULL AND resource_id IS NOT NULL))` — remplace le pattern `ownerType/ownerId` |
| `Closure` | id, salonId (nullable), resourceId (nullable), startAt, endAt, reason | Même `CHECK` que `Schedule` |
| `Appointment` | id, salonId, customerProfileId, serviceId, startAt (`TIMESTAMPTZ`), endAt (`TIMESTAMPTZ`), status, priceAtBooking, durationAtBooking, cancelledAt, cancelledBy, cancellationReason, createdAt, deletedAt | Snapshot prix/durée |
| `AppointmentResource` | appointmentId, resourceId | `UNIQUE(appointment_id, resource_id)`, index sur `resource_id` et `appointment_id`. Règle métier : un RDV n'est valide que si **toutes** ses ressources sont disponibles sur toute la période |
| `RefreshToken` | id, accountId, tokenHash, deviceInfo, expiresAt, revokedAt, createdAt | Un par session/device |
| `ConsentRecord` | id, accountId, type (TOS/PRIVACY/MARKETING_PLATFORM), version, acceptedAt | Consentement plateforme, distinct du consentement marketing par salon |
| `AuditLog` | id, actorAccountId, action, entityType, entityId, before (JSON whitelisté), after (JSON whitelisté), createdAt | Jamais de PII brute copiée — voir règle §2 |

Toutes les PK exposées en UUIDv7. Soft-delete + index uniques partiels sur `Organization`,
`Salon`, `Resource`, `Service`, `CustomerProfile`, `SalonCustomerLink`, `Appointment`.

### Règles de scoping explicites `[DECIDED]`

- Une `Schedule`/`Closure` de type ressource (`resourceId` renseigné, `salonId` null) est **toujours**
  résolue via `Resource → Salon` pour déterminer son périmètre réel. Elle ne peut jamais être
  utilisée seule pour contourner le scoping par salon.
- `AppointmentResource.resourceId` doit **obligatoirement** appartenir au même salon que
  `Appointment.salonId`. Garanti au niveau service applicatif (validation systématique avant
  écriture) **et** renforcé au niveau base de données via une contrainte d'exclusion PostgreSQL
  (`EXCLUDE` + extension `btree_gist`, voir `apontaja-schema.sql`) qui empêche mécaniquement
  tout chevauchement de réservation sur une même ressource — ce mécanisme ferme définitivement
  la classe de bug "double-booking non détecté" identifiée dans l'ancien projet (§3.3).

### Schéma relationnel complet

Le détail exhaustif (PK, FK, `NOT NULL`, `UNIQUE`, index, `CHECK`, index uniques partiels pour le
soft-delete, contraintes d'exclusion anti-chevauchement) est maintenant spécifié dans
`apontaja-schema.sql`, à conserver à la racine du dossier `back/` du futur repo comme référence
canonique du modèle. Ce fichier fait foi en cas de divergence avec le tableau ci-dessus (qui reste
volontairement une vue simplifiée).

### Règle d'accès à un salon (autorisation)

Accès à un salon si `StaffMembership` actif sur ce salon **OU** `OrganizationMembership` OWNER
sur l'organisation propriétaire — mais toute logique métier (recherche, modification, suppression
de RDV/ressources/clients...) reste toujours filtrée par `salonId`, jamais par organisation
directement.

---

## 5. Plan d'action (roadmap en tranches verticales)

### Phase 0 — Fondations (à dérouler dans cet ordre, pas en un seul bloc)
1. [ ] Squelette du mono-repo pnpm workspaces : `back/`, `portail-salon/`, `portail-client/`,
       `packages/ui-kit/`, `pnpm-workspace.yaml`
2. [ ] Spring Boot minimal (Web, Security, Data JPA, Validation) — un simple `/health` qui répond
3. [ ] Mise en place de l'architecture de packages (§2 : domaine → couches web/application/
       domain/infrastructure), sans encore de logique métier dedans
4. [ ] Écriture des règles ArchUnit correspondant au graphe de dépendances défini en §2, **avant**
       tout code métier — elles doivent échouer sur un projet vide de sens si mal câblées, puis
       passer une fois la structure en place
5. [ ] CI de base : build + tests (y compris ArchUnit) + lint sur chaque PR
6. [ ] Stratégie de gestion des secrets
7. [ ] Application du schéma `apontaja-schema.sql` via Flyway (première migration)

Seulement après ces 7 étapes validées : démarrage du vertical slice "Authentification" (Phase 1).

### Phase 1 — Vertical slice "Authentification" (back + front ensemble)
- [ ] Implémentation `Account`, `RefreshToken`, `ConsentRecord`
- [ ] JWT access (mémoire JS) + refresh (cookie httpOnly, rotation, détection de réutilisation)
- [ ] Rate limiting login/register/refresh/confirm-password
- [ ] Vérification d'email + mot de passe oublié
- [ ] Portail salon : écran login/register/mot de passe oublié fonctionnel de bout en bout
- [ ] Tests : unitaires + intégration (Testcontainers) + matrice d'autorisation dès ce stade

### Phase 2 — Vertical slice "Salon & organisation"
- [ ] `Organization`, `OrganizationMembership`, `Salon`, `StaffMembership`
- [ ] Autorisation centralisée (method security)
- [ ] Portail salon : création de salon, gestion du staff
- [ ] Tests d'autorisation systématiques (OWNER salon A vs salon B, MANAGER, EMPLOYEE, etc.)

### Phase 3 — Vertical slice "Rendez-vous" (le cœur du produit)
- [ ] `Resource`, `Service`, `Schedule`, `Closure`, `Appointment`, `AppointmentResource`
- [ ] Règles complètes : conflit de créneau (borné correctement), horaires d'ouverture,
      disponibilité ressource, fermetures — vérifiées sur toute la plage (début ET fin)
- [ ] Revalidation complète à la mise à jour d'un RDV, snapshot prix/durée
- [ ] Tests unitaires exhaustifs (chevauchement partiel, adjacent, DST, changement de fuseau)
- [ ] Portail salon : agenda fonctionnel de bout en bout

### Phase 4 — Vertical slice "Client & carnet client"
- [ ] `CustomerProfile`, `SalonCustomerLink`
- [ ] Portail salon : carnet client
- [ ] Portail client : inscription, réservation, historique multi-salons

### Phase 5 — Durcissement final
- [ ] Audit sécurité de repasse (headers, CSP, dépendances)
- [ ] Observabilité : logs structurés, jamais de données sensibles loggées
- [ ] Tests de charge basiques sur les endpoints critiques
- [ ] Politique de rétention `AuditLog` (durée de conservation, qui peut consulter, quels
      événements) — non bloquant pour la Phase 0, à traiter avant l'ouverture au public

### Phase 6 — Paiement (reportée, hors périmètre v1)
- [ ] Intégration Stripe revue : flux transactionnel, webhook comme source de vérité, idempotence

---

## 6. Questions ouvertes

Aucune question bloquante en attente. Toutes les décisions structurantes nécessaires pour
démarrer la Phase 0 sont `[DECIDED]` (section 2).

---

## 7. État d'avancement

**Session 1** : analyse complète de l'ancien repo (sécurité, logique métier, dette technique).

**Session 2** : renommage en "Apontaja", décision de ne plus réinjecter le gitingest,
confirmation des choix techniques de base, structure mono-repo actée, première proposition de
modèle de données.

**Session 3** : modèle de données finalisé (première version) — organisation, matching client
avec consolidation automatique, ConsentRecord, UUIDv7, soft-delete.

**Session 4** : revue architecturale croisée (analyse de ChatGPT sur le document de session 3).
Corrections adoptées : remplacement du pattern `ownerType/ownerId` par des FK nullables + CHECK,
contraintes explicites sur `AppointmentResource`, politique PII sur `AuditLog`, stratégie d'index
uniques partiels pour le soft-delete, clarification `Resource` vs `StaffMembership`, règles de
gestion du temps explicites, décision sur l'emplacement de l'access token + CSRF, passage à une
roadmap en tranches verticales, choix définitif de Flyway. Introduction du système de statuts
`[DECIDED]`/`[PROVISIONAL]`/`[OPEN]`. Simplification du matching `CustomerProfile` proposée,
en attente de confirmation explicite.

**Session 5** : confirmation définitive de la simplification du matching `CustomerProfile`
(`[DECIDED]`). Décision sur le partage d'UI entre portails : `packages/ui-kit` commun, repart
entièrement de zéro (aucun portage des composants `Coelho*` de l'ancien projet, récupération
ponctuelle possible si besoin). Outillage retenu : pnpm workspaces, sans Turborepo pour l'instant.

**Session 6** : dernière question ouverte tranchée — structure du backend en module unique
Gradle/Maven avec discipline de package par domaine métier, frontières imposées via des tests
ArchUnit plutôt qu'un vrai multi-module dès le départ. Plus aucune décision structurante en
attente.

**Session 7** : traitement de la deuxième revue croisée (ChatGPT). Statut CSRF corrigé en
`[PROVISIONAL]` (contradiction relevée). Structure de packages précisée avec sous-couches
`web`/`application`/`domain`/`infrastructure` par domaine et graphe explicite de dépendances
autorisées entre domaines (vérifié par ArchUnit). Règles de scoping explicites ajoutées
(`Schedule`/`Closure` de ressource toujours résolus via `Resource → Salon` ;
`AppointmentResource.resourceId` doit appartenir au même salon que `Appointment.salonId`).
Phase 0 découpée en 7 étapes séquentielles. Politique de rétention `AuditLog` ajoutée en Phase 5.
**Livrable majeur : schéma relationnel PostgreSQL complet produit dans `apontaja-schema.sql`**
(PK/FK, `NOT NULL`, `UNIQUE`, index, `CHECK`, index uniques partiels pour le soft-delete, et une
contrainte d'exclusion PostgreSQL avec triggers de synchronisation qui ferme au niveau base de
données le bug historique de double-booking non détecté).

**Session suivante — prochaine étape suggérée** : dérouler concrètement les 7 étapes de la
Phase 0 (§5), en s'appuyant sur `apontaja-schema.sql` pour la première migration Flyway.

---

## 8. Instructions pour Claude en début de session suivante

1. Lire ce fichier en entier avant de répondre.
2. Reprendre à partir de la section "État d'avancement" (§7) et du plan de la section 5.
3. Respecter les statuts de décision : ne jamais traiter une entrée `[PROVISIONAL]` comme
   définitivement actée sans confirmation explicite de l'utilisateur ; signaler les `[OPEN]`
   qui deviennent bloquants pour la tâche en cours plutôt que de trancher à sa place.
4. Ne pas re-proposer une analyse déjà faite (section 3) ni redemander les décisions déjà
   actées (`[DECIDED]`) sauf si l'utilisateur souhaite les revisiter.
5. Ne pas réclamer/réinjecter le gitingest de l'ancien repo — s'appuyer sur ce fichier.
6. En fin de session, mettre à jour ce fichier : faire évoluer les statuts de décision, ajouter
   les nouveaux problèmes/questions découverts, et compléter la section 7.
7. **Fournir systématiquement, pour chaque tranche de travail livrée** :
   - le nom de la branche Git à créer avant de commencer le travail (convention à affiner à
     l'usage — pour l'instant, un préfixe simple type `phase0/etape-X-description` ou
     `feat/description` selon le contexte, à choisir de manière cohérente d'une session à l'autre) ;
   - le message de commit correspondant à la fin du travail, une fois celui-ci validé.
   Exception explicitement actée en session : le tout premier commit du repo a été fait
   directement sur `main` (création initiale du repo GitHub), donc pas de nom de branche fourni
   pour ce commit-là.
