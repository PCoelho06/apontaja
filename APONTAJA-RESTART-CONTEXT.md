# Apontaja — Reboot du projet (anciennement "Marquei", Symfony/Vue → Spring Boot/Vue)

> **Comment utiliser ce fichier** : à chaque nouvelle session avec Claude, fournis ce fichier (project
> files ou collé) et demande de continuer à partir de "État d'avancement" (§7). En fin de session,
> demande à Claude de le **mettre à jour**, puis remplace l'ancien.
>
> **Principe** : on ne réinjecte JAMAIS un gitingest complet (coût en tokens à chaque message). Ce
> fichier capture les *décisions*, les *enseignements* et une *carte des composants* (§9). Si Claude a
> besoin du code exact d'un fichier, il le demande et tu le colles.
>
> **Statuts** : `[DECIDED]` tranché, ne pas rouvrir sauf demande · `[PROVISIONAL]` direction retenue,
> à traiter avec prudence · `[OPEN]` non tranché, à trancher avant qu'il bloque.

Dernière mise à jour : session 11. **Phase 3 ("Rendez-vous") : back tranches 1 à 7 terminées, `mvn
clean verify` vert.** Reste : tranche 8 (modification d'un RDV), tranches 9-10 (front).

---

## 1. Objectif du projet

Reprendre "Apontaja" (SaaS de gestion de rendez-vous pour salons de coiffure/beauté) dans un nouveau
repo : migrer le backend Symfony → **Spring Boot**, **durcir la sécurité**, ajouter une **vraie
stratégie de tests**, corriger les **bugs de logique métier** de l'ancien code, refondre le
**frontend** en deux portails (salon / client). Paiement (Stripe) repoussé.

## 2. Décisions techniques

### 2.1 Fondations, auth, salon (Phases 0-2)

| Sujet | Décision | Statut |
|---|---|---|
| Backend | Spring Boot 4.1 (Framework 7), Java 21, Maven | `[DECIDED]` |
| BDD / migrations | PostgreSQL, Flyway. **Les migrations Flyway sont la seule source de vérité du schéma** (`apontaja-schema.sql` n'existe plus dans le repo). V1 = schéma initial complet (toutes les tables, y compris Phase 3), V2 `account_token`, V3 `staff_invitation`, V4 unicité `organization_membership(account_id)` vivant. **Phase 3 n'a ajouté aucune migration.** `ddl-auto=validate` | `[DECIDED]` |
| Frontend | Vue 3 + TypeScript + Pinia + Tailwind 4 ; mono-repo `back/`, `portail-salon/`, `portail-client/`, `packages/ui-kit` ; pnpm workspaces sans Turborepo | `[DECIDED]` |
| Structure `back/` | Module Maven unique, package par domaine (`account`, `organization`, `salon`, `resource`, `service`, `appointment`, `customer`, `audit`) + `shared`; sous-packages `web/application/domain/infrastructure`. Règle `web → application → domain`, `infrastructure → domain`, vérifiée par ArchUnit **y compris au sein d'un domaine** (ex. `web` ne peut pas importer `domain`) | `[DECIDED]` |
| Graphe ArchUnit (`ALLOWED_DEPENDENTS`, cible → dépendants autorisés de sa couche `.application`) | account→[organization,customer,salon] · organization→[salon] · salon→[resource,appointment,service] · resource→[service,appointment] · service→[appointment] · customer→[appointment] · appointment→[] · audit→tous. Seule la couche `.application` d'un domaine est visible des autres | `[DECIDED]` |
| `shared` | Ports techniques transverses : `IdGenerator`, `EmailSender`, `OpaqueTokenGenerator`, `TokenHasher` (+ impl. dans `shared.infrastructure`). Vérifier s'il existe avant d'en créer un | `[DECIDED]` |
| Lint / CI | Checkstyle (ruleset custom, **lignes ≤ 120, imports inutilisés interdits**), GitHub Actions (job `back` = `mvn -B clean verify`, job `front` pnpm) | `[DECIDED]` |
| Secrets dev | Profil Spring `local` (`application-local.yml` gitignoré) | `[DECIDED]` |
| Tests d'intégration | Testcontainers `postgres:16-alpine` + failsafe (`*IT.java`). Tests touchant un `*JpaRepository` package-privé : dans le package `infrastructure`, `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(PostgresTestcontainersConfiguration.class)`. Entité d'un autre domaine en fixture : `TestEntityManager.persistAndFlush` | `[DECIDED]` |
| Packages test Boot 4.1 | `DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure` ; `AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure` ; `TestEntityManager` → `org.springframework.boot.jpa.test.autoconfigure` ; `AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`. Toujours vérifier | `[DECIDED]` |
| Auth | JWT HS256 15 min (mémoire JS) + refresh opaque SHA-256 30 j (cookie httpOnly/Secure/SameSite=Strict, path `/api/auth`), rotation + détection de réutilisation (granularité compte). Restauration de session au boot (`restoreSession()` avant `app.use(router)`) | `[DECIDED]` |
| CSRF | Exempt partout **sauf `/api/auth/refresh` et `/api/auth/logout`** (seuls endpoints cookie-driven). Front : `apiPostWithCsrf` réservé à refresh/logout ; tout endpoint Bearer utilise `apiGet/apiPost/apiPatch/apiDelete` avec `accessToken` (un mauvais choix → 403 silencieux) | `[DECIDED]` |
| Autorisation | Method security (`@PreAuthorize`) + guards nommés. **Pièges** : (1) `@PreAuthorize` ignoré silencieusement sur méthode non `public` ; (2) `AccessDeniedException` interceptée par le catch-all de `GlobalExceptionHandler` sauf handler explicite (déjà présent). Les handlers d'exceptions métier sont **locaux à chaque contrôleur** (pas de `@RestControllerAdvice` séparé, risque d'être court-circuité par le catch-all) | `[DECIDED]` |
| Entités à ID applicatif (UUIDv7) | `Persistable<UUID>` (`@Transient isNew` + `@PostPersist @PostLoad markNotNew()`), `createdAt` passé en paramètre (jamais `Instant.now()` dans l'entité). Clé composite : `@EmbeddedId` + `Persistable<XxxId>` | `[DECIDED]` |
| Mutateurs d'entité | Mutateurs **inconditionnels** dans l'entité ; les règles d'éligibilité (transitions, dernier OWNER…) vivent dans le service applicatif | `[DECIDED]` |
| Divers | Filtres Spring Security custom : `FilterRegistrationBean.setEnabled(false)` ; colonnes `citext` : `columnDefinition = "citext"` ; soft-delete `deletedAt` + index uniques partiels ; suppression physique pour les tables sans `deleted_at` ; UUID v7 côté app ; RGPD dès la conception ; `PageResponse<T>` maison (jamais `Page` Spring Data dans un DTO) | `[DECIDED]` |
| Permissions staff | OWNER/MANAGER/EMPLOYEE. OWNER (staff direct ou `OrganizationMembership` OWNER) gère tout ; MANAGER gère EMPLOYEE ; EMPLOYEE lecture seule ; jamais retirer/rétrograder le dernier OWNER. Un compte = une organisation (règle applicative + index V4) | `[DECIDED]` |
| Rattachement staff | Invitation email à token usage unique (TTL 7 j), pas de fusion auto compte↔invitation | `[DECIDED]` |
| Design portail salon | Palette lie-de-vin/papier/laiton, Fraunces + Inter. Non prioritaire, ne pas retoucher sans demande (branche `wip/design-and-geo-exploration` jamais mergée) | `[PROVISIONAL]` |
| Géolocalisation / timezone auto | Non retenu : `Salon.timezone` = texte libre saisi à la main | `[OPEN]` |
| Dette JS/TS, Spotless | Versions pinnées / transition TS7 ; pas de Spotless | `[OPEN]` non prioritaires |
| Paiement | Stripe hors périmètre v1 | `[DECIDED]` |

### 2.2 Phase 3 — Rendez-vous (décisions actées)

| Sujet | Décision | Statut |
|---|---|---|
| RBAC catalogue | **Écriture** (ressources, prestations, associations, horaires, fermetures) : OWNER/MANAGER (staff direct) ou OWNER d'organisation, via `catalogManagementGuard.canManageCatalog`. **Lecture** : tout le staff (`salonAccessGuard`). EMPLOYEE = lecture seule. ⚠️ Un test qui supprime une ressource/prestation doit donc s'authentifier en OWNER/MANAGER, pas EMPLOYEE | `[DECIDED]` |
| RBAC clients et RDV | **Tout le staff** (EMPLOYEE inclus) via `salonAccessGuard` : créer/lire des clients ; créer, lire, lister (agenda), confirmer/annuler/terminer/no-show des RDV | `[DECIDED]` |
| Nommage | Entité `SalonService` pour la table `service` (évite la collision avec `@Service`) | `[DECIDED]` |
| Jours de semaine | En base `schedule.day_of_week` : 0 = dimanche … 6 = samedi ; en Java/API : `DayOfWeek`/`"MONDAY"…`. **`DayOfWeekCodes` est le seul point de conversion** | `[DECIDED]` |
| Horaires | Remplacement complet de la semaine (`PUT`), plusieurs plages/jour, adjacentes OK, chevauchement refusé (400). Liste vide : sur une ressource = "suit le salon" ; sur le salon = jamais ouvert. Concurrence sérialisée par `pg_advisory_xact_lock` (pas de contrainte d'exclusion sur `schedule`) | `[DECIDED]` |
| Fermetures | Instants ISO 8601 **avec offset** (normalisés UTC) ; chevauchements entre fermetures autorisés ; suppression physique ; liste filtrable `?from&to` par recouvrement | `[DECIDED]` |
| Disponibilité (Q3) | Salon ouvert ∩ ressource disponible. Le créneau doit tenir **début ET fin** dans une plage (plages contiguës fusionnées, une pause n'est pas traversable). Ressource avec horaires propres : bornée par le salon, fermée les jours non listés. Fermeture salon bloque tout, fermeture ressource bloque la ressource ; recouvrement même partiel refuse, bornes adjacentes non. Moteur pur `AvailabilityEngine` (aucun accès base, ne regarde PAS les autres RDV) | `[DECIDED]` |
| DST / fuseau | Plages en heure locale du salon ; heure inexistante (saut de printemps) → instant de la transition ; heure ambiguë (retour d'automne) → première occurrence ; une plage a sa durée réelle du jour (00:00-06:00 = 5 h/7 h). Plage jamais à cheval sur minuit (**limite : pas de "24:00", max 23:59**). Fuseau invalide → `InvalidSalonTimezoneException` | `[DECIDED]` |
| Prestations | `ServiceResource` (clé composite) avec surcharges prix/durée ; `resolveTerms` = surcharge sinon défaut ; associations supprimées physiquement (DELETE idempotent) ; liste = seulement ressources vivantes | `[DECIDED]` |
| Suppression ressource/prestation (Q5) | Autorisée seulement si **aucun RDV actif** (ni annulé ni supprimé), sinon 409. Points d'extension `ResourceDeletionCheck` / `ServiceDeletionCheck` (définis dans `resource.application`/`service.application`, implémentés dans `appointment.application`). Côté ressource : colonne `is_active` d'`appointment_resource` (pilotée par trigger) | `[DECIDED]` |
| Clients (Q1) | `CustomerProfile` + `SalonCustomerLink` minimal (`source = MANUAL`), **aucun matching ni fusion** (chaque création = nouveau profil), pas d'update/delete (report Phase 4). Consentement marketing et dates de visite non mappés dans l'entité | `[DECIDED]` |
| Création de RDV | Corps : `customerProfileId, serviceId, resourceId, startAt` (ISO avec offset). **`endAt` jamais fourni** : `startAt + durée effective`. Snapshot `priceAtBooking`/`durationAtBooking`. v1 = une seule ressource par RDV. Ordre des vérifs : ressource (404) → prestation (404) → client (404) → association service/ressource (400) → disponibilité (409 + `issues[]`) → insertion `Appointment` puis `AppointmentResource` (contrainte d'exclusion PG 23P01 → `DataIntegrityViolationException` → 409 `AppointmentConflictException`) | `[DECIDED]` |
| `AppointmentResource` | Ne mappe QUE la clé composite. `starts_at/ends_at/is_active/during` sont pilotés par les triggers SQL (BEFORE INSERT sur la table, AFTER UPDATE sur `appointment`) : l'`Appointment` doit être inséré/flushé AVANT | `[DECIDED]` |
| Statuts (Q4) | `SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW`. **Statut initial = CONFIRMED** (en Phase 3 toute réservation est prise par le staff, téléphone/comptoir). `SCHEDULED` + `confirm()` conservés pour la réservation en ligne de la Phase 4. Transitions : confirm SCHEDULED→CONFIRMED ; cancel SCHEDULED/CONFIRMED→CANCELLED (trace `cancelledAt/By/reason`) ; **complete CONFIRMED uniquement** ; no-show SCHEDULED/CONFIRMED et seulement si `now ≥ startAt`. Déplacement (tranche 8) : seulement SCHEDULED/CONFIRMED. Transition interdite → 409 | `[DECIDED]` |
| Agenda | `GET …/appointments?resourceId=&from=&to=` : recouvrement (bornes adjacentes exclues), tous statuts (annulés inclus), trié par début, ressources résolues en lot, **sans pagination**. Deux requêtes JPQL distinctes selon `resourceId` (éviter le paramètre nul non typé `:p IS NULL OR …` sur PostgreSQL) | `[DECIDED]` |
| Divers Q6/Q7 | Option (a) ArchUnit (`salon → service` ajouté). Pas de délai minimum de réservation ni de granularité de créneau en v1 | `[DECIDED]` |

## 3. Audit de l'ancien repo — résumé

Tous les points P0 sécurité sont fermés (Phase 1). Le bug historique de **double-booking / borne de fin
manquante** est fermé côté création (moteur de disponibilité + contrainte d'exclusion PG) ; **reste la
revalidation à la mise à jour** (tranche 8). Détail exhaustif : versions antérieures de ce fichier.

## 4. Modèle de données

Principe : séparer identité technique (`Account`), rattachement staff (salon/organisation) et identité
client (`CustomerProfile`, indépendante d'un compte). Temps : RDV/fermetures en `TIMESTAMPTZ`/`Instant` ;
horaires récurrents en `LocalTime`/`DayOfWeek`.

| Domaine | Entités implémentées |
|---|---|
| `account` | `Account`, `RefreshToken`, `ConsentRecord`, `AccountToken` |
| `organization` | `Organization`, `OrganizationMembership` |
| `salon` | `Salon`, `StaffMembership`, `StaffInvitation` |
| `resource` | `Resource` (`ResourceType` EMPLOYEE/MACHINE), `Schedule`, `Closure` |
| `service` | `SalonService`, `ServiceResource` |
| `customer` | `CustomerProfile`, `SalonCustomerLink` |
| `appointment` | `Appointment`, `AppointmentResource` |
| `audit` | schéma SQL seulement (aucune plomberie d'écriture) |

Règles de scoping : accès à un salon = `StaffMembership` actif **OU** `OrganizationMembership` OWNER de
l'organisation propriétaire. Toute opération métier est filtrée par `salonId`. Une entité d'un autre
salon accédée via le chemin d'un salon donne **404**, un salon inaccessible ou inexistant donne **403**
uniforme. `Schedule`/`Closure` de ressource se résolvent via `Resource → Salon`.

## 5. Plan d'action

- **Phases 0, 1, 2** ✅ terminées.
- **Phase 3 — Rendez-vous** :
  1. [x] Resource (CRUD, RBAC catalogue)
  2. [x] Service + ServiceResource (+ ArchUnit `salon → service`)
  3. [x] Schedule + Closure
  4. [x] Moteur de disponibilité (pur, DST/fuseau)
  5. [x] CustomerProfile + SalonCustomerLink minimal
  6. [x] Création de RDV (snapshot, exclusion PG → 409, hooks de suppression)
  7. [x] Agenda (liste) + transitions de statut
  8. [ ] **Modification (déplacement) d'un RDV avec revalidation complète** — branche `feature/phase3-appointment-update`
  9. [ ] Front : catalogue (ressources, prestations, horaires, fermetures) — `feature/phase3-frontend-catalog`
  10. [ ] Front : agenda (vue, création, déplacement, annulation) — `feature/phase3-frontend-agenda` (avant de choisir FullCalendar ou un composant maison : recherche web sur la version/compatibilité Vue 3)
- **Phase 4** — Client & carnet client : `CustomerProfile` complet (update/delete, matching profil réclamé, fusion pilotée par l'utilisateur), consentement marketing, dates de visite, portail client, **réservation en ligne (statut initial SCHEDULED)**.
- **Phase 5** — Durcissement : headers CSP/HSTS, logs structurés, tests de charge, rétention `AuditLog`, secrets en production, vrai provider d'email (remplace `LoggingEmailSender`), reprise du design si nécessaire, CORS si origines séparées.
- **Phase 6** — Paiement (reporté).

## 6. Questions ouvertes

- **Tranche 8 — points de conception à soumettre AVANT de coder** : (a) déplacement seul (date/heure) ou aussi changement de ressource/prestation/client ? (b) snapshot prix/durée conservé ou recalculé si la ressource/prestation change ? (c) contrainte d'exclusion lors d'un déplacement (le RDV ne doit pas entrer en conflit avec lui-même ; l'exception 23P01 remontera de l'`UPDATE appointment` via le trigger AFTER UPDATE, pas d'un insert d'`AppointmentResource`) ; (d) revalidation = même pipeline que la création (disponibilité + résolution des termes) ; (e) statuts éligibles : SCHEDULED/CONFIRMED.
- **Fermeture créée par-dessus des RDV existants `[OPEN]`** : refuser, ou autoriser et signaler ? À traiter en tranche 8 ou plus tard.
- **`HttpMessageNotReadableException` `[OPEN]`** : un JSON syntaxiquement invalide donne un 500 (avalé par le catch-all de `GlobalExceptionHandler`) ; correctif d'un handler 400 proposé, non demandé.
- **`AppointmentResponse` sans champs d'annulation `[OPEN]`** : `cancelledAt/By/reason` non exposés ; à ajouter si le front (tranche 10) en a besoin.
- **Plages jusqu'à 24:00 `[OPEN]`** : impossible (limite `LocalTime`).
- **Doublons de clients `[OPEN]`** : deux créations avec le même email produisent deux profils (décision Q1, à revoir en Phase 4).
- **Endpoint de résolution d'identité (email) du staff `[OPEN]`** : `GET …/staff` ne renvoie que `accountId` (le front affiche l'UUID tronqué).
- **Annulation d'une invitation staff en attente `[OPEN]`** : `StaffInvitation.revoke()` existe, aucun endpoint.
- **CGU/consentement sur l'inscription via invitation `[OPEN]`** : mention absente d'`AcceptInvitationView`.
- **Gestion des secrets en production `[OPEN]`**, dette TypeScript `[OPEN]`, Spotless `[OPEN]`, géolocalisation `[OPEN]`.

## 7. État d'avancement

**Sessions 1-10** : Phases 0, 1, 2 (fondations, authentification, salon & organisation, back + front).

**Session 11 — Phase 3, back tranches 1 à 7, `mvn clean verify` vert à chaque tranche.**

- **T1 Resource** : CRUD `/api/salons/{id}/resources`, `CatalogManagementGuard`, point d'extension `ResourceDeletionCheck`.
- **T2 Service** : CRUD prestations + association ressource↔prestation (upsert de surcharges), `resolveTerms`, `ServiceDeletionCheck`, ArchUnit `salon → service`.
- **T3 Schedule/Closure** : horaires hebdomadaires en remplacement complet sous verrou consultatif, fermetures en instants.
- **T4 Disponibilité** : `AvailabilityEngine` pur + `AvailabilityService` ; tests exhaustifs DST Europe/Paris et changements de fuseau (les valeurs attendues ont été recalculées à la main).
- **T5 Clients** : profil + lien minimal, create/list/get.
- **T6 Création de RDV** : enchaînement de vérifications, snapshot, contrainte d'exclusion PG mappée en 409, test de concurrence réelle (un 201 + un 409), hooks de suppression implémentés.
- **T7 Agenda + statuts** : liste filtrée, confirm/cancel/complete/no-show, tour complet "annulation → créneau libéré → suppression autorisée".
- **Incidents/enseignements** : (1) faux `IdGenerator` constant (`new UUID(0,1)`) → doit produire des ids distincts quand un test crée plusieurs entités ; (2) Mockito strict : un stub inutilisé échoue (`lenient()` si un test sort tôt) ; (3) un test qui supprime du catalogue doit s'authentifier en OWNER/MANAGER ; (4) FK réelle sur `customer_profile.account_id` → un test d'index unique doit persister un vrai `Account` ; (5) **j'ai écrasé une correction locale de l'utilisateur** en renvoyant un fichier entier issu de ma copie de la tranche précédente → voir §8, règle 13 ; (6) le nom de branche de la tranche 7 n'a pas été fourni avant de commencer → §8, règle 7.
- **Front** : inchangé depuis la session 10 (auth, accueil = liste des salons, création de salon, détail salon = équipe/invitations, acceptation d'invitation). Stores `auth`/`salon`/`staff`, `lib/apiClient.ts`. Aucun écran Phase 3 encore.

**Prochaine étape** : tranche 8. Soumettre d'abord les points de conception de §6, puis coder.

## 8. Instructions pour Claude en début de session suivante

1. Lire ce fichier en entier ; reprendre à §7 et §5.
2. Respecter les statuts : jamais un `[PROVISIONAL]` comme acté ; signaler les `[OPEN]` qui deviennent bloquants au lieu de trancher.
3. Ne pas re-proposer d'analyse ni redemander une décision `[DECIDED]`.
4. **Ne pas réclamer le gitingest** ; si un fichier précis est nécessaire, le demander.
5. En fin de session, mettre à jour ce fichier (statuts, nouveautés, §7, §9).
6. Aucune exécution possible côté Claude (pas de réseau, mvn, pnpm ni Docker) : tout est validé par l'utilisateur, qui redonne les résultats bruts (`mvn clean verify`).
7. **Pour chaque tranche : donner le nom de branche AVANT d'écrire du code**, puis le message de commit une fois la tranche validée.
8. Avant de coder du Spring Boot 4.1, du JS/TS ou toute dépendance dont l'API a pu changer : **vérifier par recherche web**.
9. Tout contrôleur passe par la couche `application` (jamais un repository ni une entité du domaine).
10. Toute entité UUID implémente `Persistable<UUID>` avec `createdAt` en paramètre.
11. Avant un port technique transverse, vérifier `shared.domain`.
12. Rester strictement dans le scope de la tranche ; ne rien initier (design, fonctionnalité) sans proposition et accord.
13. **Fichiers déjà livrés puis modifiés par l'utilisateur** : ne JAMAIS renvoyer un fichier entier issu d'une copie interne de Claude (il écrase les corrections locales). Pour modifier un fichier existant : donner des **patchs** (avant/après). Pour une tranche : un zip ne contenant que les fichiers **nouveaux** ; les fichiers existants modifiés en patchs. Pour un correctif : uniquement les morceaux à changer.
14. Avant de livrer : vérifier à la main **lignes ≤ 120 caractères** et **imports inutilisés** (Checkstyle casse la CI sinon), fakes `IdGenerator` à ids distincts, stubs Mockito tous utilisés (ou `lenient()`).
15. Les valeurs de tests liées au temps utilisent des dates fixes (2099 = toujours futur, 2020 = toujours passé), jamais l'horloge réelle.
16. Pour une tranche à décisions ouvertes : présenter les hypothèses/choix par défaut, puis coder ; ne poser une question que si le défaut est risqué.

## 9. Annexe — carte des composants (signatures utiles)

### Guards (SpEL, par nom de bean, méthodes `public`, signatures primitives)
`@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)` · `@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)` · `@staffManagementGuard.canManageRole / canManageMembership / canChangeStaffRole`.

### Services applicatifs réutilisables
- `resource.application` : `ResourceQueryService.findAliveBySalonId(salonId)`, `.findAliveInSalon(salonId, resourceId): Optional<ResourceSummary>` · `ResourceSummary(resourceId, salonId, name, type, createdAt)` · `AvailabilityService.check(salonId, resourceId, Instant start, Instant end): AvailabilityCheck(boolean available, List<AvailabilityIssue> issues)` (issues : `OUTSIDE_SALON_HOURS, OUTSIDE_RESOURCE_HOURS, SALON_CLOSED, RESOURCE_CLOSED`) · `ResourceDeletionCheck.hasBlockingAppointments(resourceId)` · exceptions `ResourceNotFoundException`, `InvalidTimeRangeException(msg)`, `InvalidSalonTimezoneException`.
- `service.application` : `ServiceQueryService.findAliveInSalon(salonId, serviceId): Optional<ServiceSummary>`, `.resolveTerms(salonId, serviceId, resourceId): Optional<ServiceTerms(priceCents, durationMinutes)>` (vide si service/ressource absent du salon ou non associé) · `ServiceNotFoundException` · `ServiceDeletionCheck`.
- `customer.application` : `CustomerQueryService.findAliveInSalon(salonId, customerProfileId): Optional<CustomerSummary>`, `.findAliveBySalonId(salonId)`.
- `salon.application` : `SalonQueryService.findAliveById(salonId): Optional<SalonSummary(salonId, organizationId, name, address, postalCode, city, country, phone, timezone)>`.

### Domaine `appointment`
- `Appointment(id, salonId, customerProfileId, serviceId, startAt, endAt, priceAtBookingCents, durationAtBookingMinutes, createdAt)` → statut `CONFIRMED`. Champs mappés : `start_at, end_at` (modifiables), `status`, `cancelled_at/by`, `cancellation_reason`, `deleted_at` (jamais renseigné). Mutateurs inconditionnels : `confirm()`, `cancel(Instant at, UUID by, String reason)`, `complete()`, `markNoShow()`. **Pas encore de mutateur pour changer `startAt/endAt`** (tranche 8).
- `AppointmentResource(appointmentId, resourceId)` (clé seule).
- Ports : `AppointmentRepository` (`save` = saveAndFlush, `findAliveById`, `existsActiveByServiceId`, `findOverlappingBySalonId(salonId, resourceId|null, from, to)`), `AppointmentResourceRepository` (`save`, `existsActiveByResourceId` [SQL natif], `findResourceIdsByAppointmentId`, `findByAppointmentIds`).
- Application : `AppointmentCreationService.create(CreateAppointmentCommand(salonId, customerProfileId, serviceId, resourceId, String startAt))` · `AppointmentQueryService.findAliveInSalon(salonId, appointmentId)`, `.list(salonId, resourceId|null, String from|null, String to|null)` · `AppointmentStatusService.confirm/cancel(…, cancelledBy, reason)/complete/markNoShow(salonId, appointmentId)` · `AppointmentSummary(appointmentId, salonId, customerProfileId, serviceId, resourceId, startAt, endAt, status, priceAtBookingCents, durationAtBookingMinutes, createdAt)` · adaptateurs `ResourceDeletionCheckAdapter`, `ServiceDeletionCheckAdapter`.
- Exceptions → HTTP : `ResourceNotFoundException/ServiceNotFoundException/CustomerNotFoundException/AppointmentNotFoundException` → 404 ; `ServiceResourceNotAssociatedException/InvalidTimeRangeException` → 400 ; `AppointmentNotAvailableException` (avec `issues()`) / `AppointmentConflictException` / `InvalidAppointmentTransitionException` → 409.

### Endpoints (tous sous `/api/salons/{salonId}`)
- `resources` POST/GET, `resources/{id}` GET/PUT/DELETE, `resources/{rid}/schedule` GET/PUT, `resources/{rid}/closures` POST/GET + `/{id}` PUT/DELETE
- `schedule` GET/PUT, `closures` POST/GET + `/{id}` PUT/DELETE
- `services` POST/GET, `services/{id}` GET/PUT/DELETE, `services/{id}/resources` GET, `services/{id}/resources/{rid}` PUT/DELETE
- `customers` POST/GET, `customers/{customerProfileId}` GET
- `appointments` POST/GET(agenda), `appointments/{id}` GET, `appointments/{id}/confirm|cancel|complete|no-show` PATCH (`cancel` : corps `{}` ou `{"reason":"…"}`)

### Conventions de tests (à respecter)
- ITs web : `@SpringBootTest(webEnvironment = MOCK, properties = "apontaja.security.rate-limiting.enabled=false") @AutoConfigureMockMvc @Import(PostgresTestcontainersConfiguration.class)` ; données créées via les repositories du domaine, assertions via `MockMvc` ; id extrait d'une réponse via `new ObjectMapper().readTree(...)` (pas JsonPath).
- Chaque nouveau domaine expose : test repository IT, tests unitaires des services (Mockito), IT contrôleur, et une **matrice d'autorisation** (OWNER d'organisation sans staff direct, cross-organisation, cross-salon même organisation, sans authentification, salon inexistant).
- États inatteignables par l'API publique (statut, `deletedAt`, `accountId`) : posés **par réflexion** dans le test, avec un commentaire qui le justifie.
- Concurrence : deux threads + `CountDownLatch`, statuts attendus `containsExactlyInAnyOrder(…)`.
