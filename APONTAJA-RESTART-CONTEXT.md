# Apontaja — Reboot du projet (anciennement "Marquei", Symfony/Vue → Spring Boot/Vue)

> **Comment utiliser ce fichier** : il est dans les *project files*. Chaque discussion avec Claude couvre
> **une seule tranche** : l'utilisateur ouvre une nouvelle discussion avec le *prompt de reprise* fourni à
> la fin de la tranche précédente (voir §8, règle 21). Claude lit ce fichier, reprend à "État
> d'avancement" (§7) et ne demande que les fichiers dont il a besoin.
>
> **Principe** : on ne réinjecte JAMAIS un gitingest complet (coût en tokens à chaque message). Ce
> fichier capture les *décisions*, les *enseignements* et une *carte des composants* (§9). Si Claude a
> besoin du code exact d'un fichier, il le demande et l'utilisateur le colle.
>
> **Statuts** : `[DECIDED]` tranché, ne pas rouvrir sauf demande · `[PROVISIONAL]` direction retenue,
> à traiter avec prudence · `[OPEN]` non tranché, à trancher avant qu'il bloque.

Dernière mise à jour : fin de la session 12. **Phases 0, 1, 2 et 3 terminées** (Phase 3 : back, catalogue,
ui-kit, agenda, clôture back, identité du compte). `mvn clean verify` et `pnpm lint / typecheck / test /
build` verts. **Prochaine étape : Phase 4** (client & carnet client, réservation en ligne) — à découper en
tranches au début de la prochaine discussion (§5, §6).

---

## 1. Objectif du projet

Reprendre "Apontaja" (SaaS de gestion de rendez-vous pour salons de coiffure/beauté) dans un nouveau
repo : migrer le backend Symfony → **Spring Boot**, **durcir la sécurité**, ajouter une **vraie
stratégie de tests**, corriger les **bugs de logique métier** de l'ancien code, refondre le
**frontend** en deux portails (salon / client). Paiement (Stripe) repoussé.

## 2. Décisions techniques

### 2.1 Fondations, auth, salon (Phases 0-2) et transverse

| Sujet | Décision | Statut |
|---|---|---|
| Backend | Spring Boot 4.1 (Framework 7), Java 21, Maven | `[DECIDED]` |
| BDD / migrations | PostgreSQL, Flyway. **Les migrations Flyway sont la seule source de vérité du schéma.** V1 = schéma initial complet (toutes les tables), V2 `account_token`, V3 `staff_invitation`, V4 unicité `organization_membership(account_id)` vivant, V5 unicité `resource(staff_membership_id)` vivant (`uq_resource_staff_membership_alive`). `ddl-auto=validate`. **Tant qu'aucun environnement n'est déployé, V1 est modifiée en place** (ex. `account.first_name/last_name` ajoutés dans V1) : la base locale est recréée (supprimer le volume Docker), **pas de migration de rattrapage, pas de colonne nullable "pour d'anciennes données"**. Dès le premier déploiement, une migration appliquée ne se modifie plus jamais | `[DECIDED]` |
| Frontend | Vue 3 + TypeScript + Pinia + Tailwind 4 ; mono-repo `back/`, `portail-salon/`, `portail-client/`, `packages/ui-kit` ; pnpm workspaces sans Turborepo. Versions : Vue 3.5, vue-router 5 (release de transition, API v4 inchangée), Pinia 4 (options stores), Vite 8, Vitest 4, TypeScript 7 natif (+ alias `typescript` = TS6 pour l'API JS de vue-tsc) | `[DECIDED]` |
| Lint / CI front | ESLint `--max-warnings 0`. Les `.vue` n'ont pas les globales navigateur : utiliser `globalThis` (ex. `globalThis.confirm`), jamais `window`, et **ne pas citer de types DOM** (`HTMLElement`, `MouseEvent`…) dans le `<script>` d'un `.vue` (`no-undef`) — dans un `.ts` c'est permis. `pnpm lint --fix` autorisé (il réordonne les attributs de template). CI : lint, **typecheck** (`pnpm -r --if-present typecheck`), build, test | `[DECIDED]` |
| Dépendances front | Pas de nouvelle dépendance sans recherche web, justification et accord. VueUse évalué en 10d et écarté (`useDraggable` : un seul élément, pas d'annulation ; le reste = 10 lignes). À reconsidérer pour un besoin précis (anti-rebond, `useStorage`, points de rupture) | `[DECIDED]` |
| `@apontaja/ui-kit` | Paquet consommé **en source** (`exports` : `.` → `src/index.ts`, `./theme.css`), `vue` en peerDependency, versions alignées sur `portail-salon`. **Tokens** (`paper, ink, wine, wine-hover, brass, border, danger`, polices) dans `src/theme.css`, importé par chaque appli avec `@source` Tailwind (Tailwind 4 ne scanne que le dossier du CSS d'entrée). Les composants n'utilisent **que** ces tokens. Composants : `UiButton` (variantes primary/secondary/danger, `size` md/sm, `loading`), `UiInput`, `UiSelect`, `UiTextarea` (via `UiField` : libellé associé, `aria-invalid`, `aria-describedby`, `hint`, `error`), `UiCheckbox`, `UiAlert`, `UiBadge`, `UiDialog` (`<dialog>` natif, `v-model:open`, corps rendu seulement à l'ouverture, `autofocus` sur le premier champ). Un composant n'est ajouté que si un écran en a besoin. **Tout écran nouveau ou modifié utilise le ui-kit** | `[DECIDED]` |
| Structure `back/` | Module Maven unique, package par domaine (`account`, `organization`, `salon`, `resource`, `service`, `appointment`, `customer`, `audit`) + `shared`; sous-packages `web/application/domain/infrastructure`. Règle `web → application → domain`, `infrastructure → domain`, vérifiée par ArchUnit **y compris au sein d'un domaine** (ex. `web` ne peut pas importer `domain`) | `[DECIDED]` |
| Graphe ArchUnit (`ALLOWED_DEPENDENTS`, cible → dépendants autorisés de sa couche `.application`) | account→[organization,customer,salon] · organization→[salon] · salon→[resource,appointment,service] · resource→[service,appointment] · service→[appointment] · customer→[appointment] · appointment→[] · audit→tous. Seule la couche `.application` d'un domaine est visible des autres. **Tout nouveau domaine ou nouvelle dépendance inter-domaines demande de mettre à jour ce graphe (proposer avant de coder)** | `[DECIDED]` |
| `shared` | Ports techniques transverses : `IdGenerator`, `EmailSender`, `OpaqueTokenGenerator`, `TokenHasher` (+ impl. dans `shared.infrastructure`). Vérifier s'il existe avant d'en créer un | `[DECIDED]` |
| Lint / CI back | Checkstyle (ruleset custom, **lignes ≤ 120, imports inutilisés interdits**), GitHub Actions (job `back` = `mvn -B clean verify`, job `front` pnpm) | `[DECIDED]` |
| Secrets dev | Profil Spring `local` (`application-local.yml` gitignoré) | `[DECIDED]` |
| Tests d'intégration | Testcontainers `postgres:16-alpine` + failsafe (`*IT.java`) — **Docker doit tourner** (sinon les `@SpringBootTest` de Surefire échouent au chargement du contexte). Tests touchant un `*JpaRepository` package-privé : dans le package `infrastructure`, `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(PostgresTestcontainersConfiguration.class)`. Entité d'un autre domaine en fixture : `TestEntityManager.persistAndFlush`. Les ITs `@SpringBootTest` peuvent croiser les domaines | `[DECIDED]` |
| Packages test Boot 4.1 | `DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure` ; `AutoConfigureTestDatabase` → `org.springframework.boot.jdbc.test.autoconfigure` ; `TestEntityManager` → `org.springframework.boot.jpa.test.autoconfigure` ; `AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`. `PostgresTestcontainersConfiguration` → `com.apontaja.back.testsupport`. Toujours vérifier | `[DECIDED]` |
| Auth | JWT HS256 15 min (mémoire JS) + refresh opaque SHA-256 30 j (cookie httpOnly/Secure/SameSite=Strict, path `/api/auth`), rotation + détection de réutilisation (granularité compte). Restauration de session au boot (`restoreSession()` avant `app.use(router)`). **Après `login` ou `refresh`, l'identité (prénom, nom, email) est chargée via `GET /api/account/me`** (le store `auth` n'utilise pas la réponse de `/login` pour l'identité) | `[DECIDED]` |
| Identité du compte | `account.first_name` et `last_name` `NOT NULL` + `CHECK` non vides (dans V1). `Account(id, email, firstName, lastName, passwordHash, createdAt)` : noms **trimés, 1 à 100 caractères** (sinon `IllegalArgumentException`). L'inscription exige les deux (`@NotBlank @Size(max=100)`) ; la création de compte par invitation aussi. `/api/account/me` renvoie `firstName`, `lastName`. **Nom d'affichage** = prénom + initiale du nom en majuscule + point (« Pierre D. »), calculé par `AccountSummary.displayName()`. Pas encore de modification du profil (nom, email, mot de passe) | `[DECIDED]` |
| CSRF | Exempt partout **sauf `/api/auth/refresh` et `/api/auth/logout`** (seuls endpoints cookie-driven). Front : `apiPostWithCsrf` réservé à refresh/logout ; tout endpoint Bearer utilise `apiGet/apiPost/apiPut/apiPatch/apiDelete` avec `accessToken` (un mauvais choix → 403 silencieux) | `[DECIDED]` |
| Autorisation | Method security (`@PreAuthorize`) + guards nommés. **Pièges** : (1) `@PreAuthorize` ignoré silencieusement sur méthode non `public` ; (2) `AccessDeniedException` interceptée par le catch-all de `GlobalExceptionHandler` sauf handler explicite (déjà présent). Les handlers d'exceptions métier sont **locaux à chaque contrôleur** ; `GlobalExceptionHandler` (`com.apontaja.back.web`) ne porte que le générique : validation (400 + `fieldErrors`), accès refusé (403), **JSON illisible (400 "Corps de requête illisible.")**, inattendu (500 sans détail) | `[DECIDED]` |
| Entités à ID applicatif (UUIDv7) | `Persistable<UUID>` (`@Transient isNew` + `@PostPersist @PostLoad markNotNew()`), `createdAt` passé en paramètre (jamais `Instant.now()` dans l'entité). Clé composite : `@EmbeddedId` + `Persistable<XxxId>` | `[DECIDED]` |
| Mutateurs d'entité | Mutateurs **inconditionnels** dans l'entité ; les règles d'éligibilité (transitions, dernier OWNER, compatibilité de lien…) vivent dans le service applicatif | `[DECIDED]` |
| Divers | Filtres Spring Security custom : `FilterRegistrationBean.setEnabled(false)` ; colonnes `citext` : `columnDefinition = "citext"` ; soft-delete `deletedAt` + index uniques partiels ; suppression physique pour les tables sans `deleted_at` ; UUID v7 côté app ; RGPD dès la conception ; `PageResponse<T>` maison (jamais `Page` Spring Data dans un DTO) | `[DECIDED]` |
| Permissions staff | OWNER/MANAGER/EMPLOYEE. OWNER (staff direct ou `OrganizationMembership` OWNER) gère tout ; MANAGER gère EMPLOYEE ; EMPLOYEE lecture seule ; jamais retirer/rétrograder le dernier OWNER. Un compte = une organisation (règle applicative + index V4) | `[DECIDED]` |
| Rattachement staff | Invitation email à token usage unique (TTL 7 j), pas de fusion auto compte↔invitation | `[DECIDED]` |
| Fuseau du salon | `Salon.timezone` validé à la création : `@IanaTimezone` (`salon.web`) accepte **uniquement** un identifiant de région connu de la JVM, à l'identique (`ZoneId.getAvailableZoneIds()` : ni "Paris", ni "+02:00", ni espaces, ni casse différente) → 400 `fieldErrors.timezone`. Front : liste déroulante `Intl.supportedValuesOf("timeZone")` à la création. L'agenda et les fermetures vérifient aussi l'identifiant via `Intl` et se désactivent s'il est invalide | `[DECIDED]` |
| Design portail salon | Palette lie-de-vin/papier/laiton, Fraunces + Inter (tokens dans le ui-kit). Non prioritaire, ne pas retoucher sans demande (branche `wip/design-and-geo-exploration` jamais mergée). Agenda et catalogue sont sur le ui-kit ; restent hors ui-kit : `HomeView`, `SalonDetailView`, `CreateSalonView`, écrans d'authentification (`AuthLayout`, `AuthTextField`) et les `<input type="time">` natifs de `ScheduleView` | `[PROVISIONAL]` |
| Paiement | Stripe hors périmètre v1 | `[DECIDED]` |
| Dette JS/TS, Spotless | Versions pinnées / transition TS7 ; pas de Spotless | `[OPEN]` non prioritaires |

### 2.2 Phase 3 — Rendez-vous (décisions actées, back)

| Sujet | Décision | Statut |
|---|---|---|
| RBAC catalogue | **Écriture** (ressources, prestations, associations, horaires, fermetures) : OWNER/MANAGER (staff direct) ou OWNER d'organisation, via `catalogManagementGuard.canManageCatalog`. **Lecture** : tout le staff (`salonAccessGuard`). EMPLOYEE = lecture seule. ⚠️ Un test qui supprime une ressource/prestation doit donc s'authentifier en OWNER/MANAGER, pas EMPLOYEE | `[DECIDED]` |
| RBAC clients et RDV | **Tout le staff** (EMPLOYEE inclus) via `salonAccessGuard` : créer/lire des clients ; créer, lire, lister (agenda), déplacer, confirmer/annuler/terminer/no-show des RDV | `[DECIDED]` |
| Nommage | Entité `SalonService` pour la table `service` (évite la collision avec `@Service`) | `[DECIDED]` |
| Jours de semaine | En base `schedule.day_of_week` : 0 = dimanche … 6 = samedi ; en Java/API : `DayOfWeek`/`"MONDAY"…`. **`DayOfWeekCodes` est le seul point de conversion** | `[DECIDED]` |
| Horaires | Remplacement complet de la semaine (`PUT`), plusieurs plages/jour, adjacentes OK, chevauchement refusé (400). Liste vide : sur une ressource = "suit le salon" ; sur le salon = jamais ouvert. Concurrence sérialisée par `pg_advisory_xact_lock`. API : `{"slots":[{"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"12:00"}]}` (chaînes) | `[DECIDED]` |
| Fermetures | Instants ISO 8601 **avec offset** (normalisés UTC) ; chevauchements entre fermetures autorisés ; suppression physique ; liste filtrable `?from&to` par recouvrement | `[DECIDED]` |
| Fermeture au-dessus de RDV (8b) | **Refusée en 409** à la création et à la modification d'une fermeture (salon ou ressource) si elle recouvre un RDV `SCHEDULED`/`CONFIRMED` (annulé/terminé/no-show ne bloquent pas). Réponse 409 : `count` + `appointments[]` (20 max, triés par début). Suppression jamais bloquée. Fermetures déjà posées non revalidées. Hook `ClosureConflictCheck` (resource.application) implémenté par `ClosureConflictCheckAdapter` (appointment.application). Pas de mode `force` | `[DECIDED]` |
| Disponibilité (Q3) | Salon ouvert ∩ ressource disponible. Le créneau doit tenir **début ET fin** dans une plage (plages contiguës fusionnées, une pause n'est pas traversable). Ressource avec horaires propres : bornée par le salon, fermée les jours non listés. Fermeture salon bloque tout, fermeture ressource bloque la ressource. Moteur pur `AvailabilityEngine` (aucun accès base, ne regarde PAS les autres RDV) | `[DECIDED]` |
| DST / fuseau | Plages en heure locale du salon ; heure inexistante → instant de la transition ; heure ambiguë → première occurrence ; une plage a sa durée réelle du jour. Plage jamais à cheval sur minuit (**limite : pas de "24:00", max 23:59**). Fuseau invalide → `InvalidSalonTimezoneException` | `[DECIDED]` |
| Prestations | `ServiceResource` (clé composite) avec surcharges prix/durée ; `resolveTerms` = surcharge sinon défaut ; associations supprimées physiquement (DELETE idempotent) ; liste = seulement ressources vivantes | `[DECIDED]` |
| Suppression ressource/prestation (Q5) | Autorisée seulement si **aucun RDV actif** (ni annulé ni supprimé), sinon 409. Points d'extension `ResourceDeletionCheck` / `ServiceDeletionCheck` | `[DECIDED]` |
| Lien ressource ↔ membre de l'équipe (8c) | `resource.staff_membership_id` (nullable) exposé (`staffMembershipId` ; `PUT` = remplacement complet, `null` délie). **Seul un EMPLOYEE peut être lié** (400). Membre vivant du même salon (404). **Un membre n'est lié qu'à une seule ressource vivante** (409 + index V5). `ResourceManagementService.create(command, staffMembershipId)` / `.update(…, staffMembershipId)` (les anciennes surcharges sans lien ont été supprimées) | `[DECIDED]` |
| Liste du staff | `GET …/staff` : `{staffMembershipId, accountId, displayName, email, role, since}`. **`displayName` pour tout le staff ; `email` seulement pour un OWNER/MANAGER du salon ou un OWNER de l'organisation** (`StaffManagementGuard.canViewStaffEmails`), `null` sinon. Compte supprimé : `displayName` = "Compte supprimé", pas d'email. Services : `StaffMemberQueryService.listMembers(salonId, actorAccountId)`, `.existsAliveInSalon(salonId, staffMembershipId)` | `[DECIDED]` |
| Clients (Q1) | `CustomerProfile` + `SalonCustomerLink` minimal (`source = MANUAL`), **aucun matching ni fusion** (chaque création = nouveau profil), pas d'update/delete (report Phase 4). Au moins un email ou un téléphone (`ContactRequiredException`, 400). `POST` répond **201 avec `CustomerResponse`** (contient `customerProfileId`). Liste = tableau simple non paginé. Consentement marketing et dates de visite non mappés | `[DECIDED]` |
| Création de RDV | Corps : `customerProfileId, serviceId, resourceId, startAt` (ISO avec offset). **`endAt` jamais fourni** : `startAt + durée effective`. Snapshot `priceAtBooking`/`durationAtBooking`. v1 = une seule ressource par RDV. Ordre des vérifs : ressource (404) → prestation (404) → client (404) → association (400) → disponibilité (409 + `issues[]`) → insertion `Appointment` puis `AppointmentResource` (exclusion PG 23P01 → 409 `AppointmentConflictException`) | `[DECIDED]` |
| Déplacement de RDV (8) | `PATCH …/appointments/{id}/reschedule`, corps `{"startAt":"…"}`, tout le staff. **Déplacement seul** (ressource, prestation, client figés). **Snapshot conservé** : `endAt = nouveau startAt + durationAtBookingMinutes`. Revalidation = `AvailabilityService.check` (409 + `issues[]`). Statuts éligibles `SCHEDULED`/`CONFIRMED`, sinon 409. Conflit avec un autre RDV : exclusion PG levée par l'`UPDATE appointment` → 409. Pas de contrôle "date passée". Ferme le bug historique de double-booking | `[DECIDED]` |
| `AppointmentResource` | Ne mappe QUE la clé composite. `starts_at/ends_at/is_active/during` sont pilotés par les triggers SQL : l'`Appointment` doit être inséré/flushé AVANT | `[DECIDED]` |
| Statuts (Q4) | `SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW`. **Statut initial = CONFIRMED** (réservation prise par le staff). `SCHEDULED` + `confirm()` conservés pour la réservation en ligne (Phase 4). Transitions : confirm SCHEDULED→CONFIRMED ; cancel SCHEDULED/CONFIRMED→CANCELLED (`cancelledAt/By/reason`) ; complete CONFIRMED uniquement ; no-show SCHEDULED/CONFIRMED et seulement si `now ≥ startAt`. Transition interdite → 409 | `[DECIDED]` |
| Agenda | `GET …/appointments?resourceId=&from=&to=` : recouvrement (bornes adjacentes exclues), tous statuts, trié par début, **sans pagination**. Deux requêtes JPQL distinctes selon `resourceId` (éviter `:p IS NULL OR …` sur PostgreSQL) | `[DECIDED]` |
| Divers Q6/Q7 | Option (a) ArchUnit (`salon → service` ajouté). **Pas de délai minimum de réservation ni de granularité de créneau en v1 (à revoir pour la réservation en ligne, Phase 4)** | `[DECIDED]` |

### 2.3 Phase 3 — Front portail salon (décisions actées)

| Sujet | Décision | Statut |
|---|---|---|
| Routes | `/salons/:salonId/catalogue` (layout) avec enfants `ressources`, `prestations`, `horaires`, `fermetures` (noms `catalog-resources|services|schedule|closures`) ; `/salons/:salonId/agenda` (`agenda`). `CatalogLayoutView` charge le salon et la liste des salons avant d'afficher l'enfant | `[DECIDED]` |
| Droits dans l'UI | Rôle lu dans `salon.salons[].role` ; `canManage` = OWNER, MANAGER ou ORGANIZATION_OWNER (composable `useCatalogAccess`). Cosmétique : le back reste l'autorité. Limite : première page de 20 salons seulement | `[DECIDED]` |
| Stores | Un store Pinia par sujet (`resource`, `salonService`, `schedule` et `appointment` sans état, `closure`, `customer`, `agenda`, `staff`, `salon`, `auth`), requêtes via `lib/apiClient.ts` + `requireToken()`. Après une écriture, le store **recharge la liste** | `[DECIDED]` |
| Utilitaires | `lib/catalogFormat.ts`, `errorMessage.ts`, `requireToken.ts`, `schedule.ts`, `zonedTime.ts` (heure locale ↔ instant ISO via `Intl`, sans bibliothèque : heure inexistante refusée, ambiguë = seconde occurrence), `closureConflict.ts`, `agenda.ts`, `agendaDrag.ts`, `appointmentStatus.ts`, `appointmentActions.ts`, `availabilityIssues.ts`. `apiClient` : `apiPut`, `ApiError.problem` (corps complet du `ProblemDetail`) | `[DECIDED]` |
| Argent | Prix saisis en euros, centimes côté API ; **devise EUR supposée** (le modèle n'a pas de devise) | `[PROVISIONAL]` |
| Catalogue (UI) | Ressources, prestations (+ panneau d'associations avec surcharges), horaires (éditeur de semaine dans la vue), fermetures (saisie en heure du salon, liste "en cours et à venir", 409 de la 8b avec `count` et liste). Lien ressource↔staff : sélecteur limité aux membres libres, "membre retiré" vidé à l'édition ; le sélecteur affiche le `displayName` | `[DECIDED]` |
| Agenda (UI) | Vue **Jour**, une colonne par ressource, jour = celui du **salon**. Grille bornée par les horaires du salon (08:00-20:00 par défaut). Fond : créneaux ouverts = salon ∩ ressource moins fermetures. RDV en **heure murale**, colorés par statut, **annulés masqués** par défaut. Filtre "Mon agenda" (ressource liée au compte connecté), sélectionné par défaut s'il existe. Une journée = RDV, horaires et fermetures du salon + 2 requêtes par ressource, appliqués d'un coup, dernière demande gagnante | `[DECIDED]` |
| Créer / gérer un RDV (UI) | Création au clic sur un **créneau ouvert** (quart d'heure) ou bouton ; prestations limitées à celles associées à la ressource ; client existant (recherche) ou nouveau (un client créé reste sélectionné si la réservation échoue). Détail : confirmer, terminer, client absent, annuler (motif optionnel), déplacer. **Boutons affichés seulement quand l'action est possible** (`availableActions`, `canReschedule`). Erreurs : `issues[]` traduites en français | `[DECIDED]` |
| Déplacer un RDV (UI) | Formulaire (nouveau début, durée et prix conservés) + **glisser-déposer** vertical, même colonne uniquement, événements de pointeur (seuil 5 px, quart d'heure, bornes de la grille, repère rouge et refus local si hors créneau ouvert, Échap/`pointercancel` annulent, clic post-glisser ignoré). Non déplaçables : statuts non éligibles et RDV débordant du jour (`clipped`) | `[DECIDED]` |
| Identité (UI) | Prénom et nom à l'inscription et à la création de compte par invitation ; `AcceptInvitationView` affiche les erreurs de connexion/inscription (premier message de champ, sinon message du back) ; équipe : `displayName` pour tous, email en petit pour les gestionnaires ; création de salon : fuseau en liste déroulante | `[DECIDED]` |
| Tests front | Vitest, tests à côté de leur source (`*.test.ts` ou `*.spec.ts`) ; stores testés avec `vi.mock("@/lib/apiClient", importOriginal)` + `useAuthStore().accessToken = "tok"`, ou en simulant `fetch` **par chemin avec une `Response` neuve à chaque appel** (le corps d'une `Response` ne se lit qu'une fois) ; `jsdom` n'implémente pas `<dialog>.showModal()/close()` (stubs dans les tests du ui-kit) | `[DECIDED]` |

## 3. Audit de l'ancien repo — résumé

Tous les points P0 sécurité sont fermés (Phase 1). Le bug historique de **double-booking / borne de fin
manquante** est **fermé** : création (moteur de disponibilité + contrainte d'exclusion PG) et mise à jour
(tranche 8). Détail exhaustif : versions antérieures de ce fichier.

## 4. Modèle de données

Principe : séparer identité technique (`Account`), rattachement staff (salon/organisation) et identité
client (`CustomerProfile`, indépendante d'un compte). Temps : RDV/fermetures en `TIMESTAMPTZ`/`Instant` ;
horaires récurrents en `LocalTime`/`DayOfWeek`.

| Domaine | Entités implémentées |
|---|---|
| `account` | `Account` (email, prénom, nom, mot de passe haché), `RefreshToken`, `ConsentRecord`, `AccountToken` |
| `organization` | `Organization`, `OrganizationMembership` |
| `salon` | `Salon`, `StaffMembership`, `StaffInvitation` |
| `resource` | `Resource` (`ResourceType` EMPLOYEE/MACHINE, `staffMembershipId` nullable), `Schedule`, `Closure` |
| `service` | `SalonService`, `ServiceResource` |
| `customer` | `CustomerProfile`, `SalonCustomerLink` |
| `appointment` | `Appointment`, `AppointmentResource` |
| `audit` | schéma SQL seulement (aucune plomberie d'écriture) |

Règles de scoping : accès à un salon = `StaffMembership` actif **OU** `OrganizationMembership` OWNER de
l'organisation propriétaire. Toute opération métier est filtrée par `salonId`. Une entité d'un autre
salon accédée via le chemin d'un salon donne **404**, un salon inaccessible ou inexistant donne **403**
uniforme.

Une **ressource** (ce qui se réserve) n'est pas un **membre de l'équipe** (compte qui se connecte) ; le
lien est optionnel. Retirer un membre est un soft-delete : une ressource peut garder un
`staffMembershipId` orphelin (§6).

## 5. Plan d'action

- **Phases 0, 1, 2, 3** ✅ terminées (Phase 3 : tranches 1-8, 8b, 8c, 9a, 9a-bis, 9b, ui-kit 10-0, agenda 10a-10d, clôture back 11, catalogue sur ui-kit 9c, identité du compte 12).
- **Phase 4 — Client & carnet client, réservation en ligne** *(découpage `[PROVISIONAL]`, à raffiner et à faire valider au début de la prochaine discussion, avec les décisions de §6)* :
  - **4a Carnet client côté salon** : fiche client (modifier ; supprimer = anonymisation RGPD), consentement marketing, dates de visite (dérivées des RDV), historique des RDV d'un client, liste paginée et recherche (remplace la liste non paginée).
  - **4b Doublons** : détection (email/téléphone), fusion pilotée par l'utilisateur, profil "réclamé".
  - **4c Compte client et portail** : `portail-client` (authentification d'un client = `Account`), rattachement `CustomerProfile.accountId`, matching du profil réclamé, consentements.
  - **4d Réservation en ligne** : disponibilités publiques d'un salon (créneaux calculés par le moteur de disponibilité **en tenant compte des RDV existants**), choix prestation/ressource, statut initial `SCHEDULED`, validation par le salon (écran dans l'agenda), annulation par le client, règles (délai minimum, granularité), notifications email.
- **Phase 5** — Durcissement : headers CSP/HSTS, logs structurés, tests de charge, rétention `AuditLog`, secrets en production, vrai provider d'email (remplace `LoggingEmailSender`), reprise du design si nécessaire, CORS si origines séparées, **verrou consultatif réservation/fermeture**, endpoint agrégé pour l'agenda, modification du profil du compte (nom, email, mot de passe), pagination de la liste des salons.
- **Phase 6** — Paiement (reporté).

## 6. Questions ouvertes

**À trancher au début de la Phase 4** (proposer des défauts, ne pas coder avant accord) :
- Périmètre minimal v1 de la réservation en ligne : confirmation **automatique ou manuelle** par le salon ? Le client peut-il réserver sans compte (invité) ? Annuler/déplacer lui-même ?
- Disponibilités publiques : granularité des créneaux, **délai minimum de réservation** (Q7 : aucun en v1 côté staff), horizon maximal, choix de la ressource par le client ou attribution automatique, surcharge de prix/durée par ressource.
- **Anti-abus** et limitation de débit sur les endpoints publics ; protection contre la réservation concurrente (déjà couverte par la contrainte d'exclusion PG).
- **RGPD** : consentements (CGU, confidentialité, marketing), conservation, droit à l'effacement (anonymisation d'un `CustomerProfile` lié à des RDV), export.
- **Doublons de clients** : règle de rapprochement (email, téléphone), qui valide une fusion, sort du profil "réclamé" quand un compte est créé avec le même email.
- **Notifications** : quels emails (confirmation, rappel, annulation) tant qu'il n'y a que `LoggingEmailSender` ?
- Domaine(s) ArchUnit à créer ou étendre (ex. un domaine `booking` ?) : proposer le graphe avant de coder.

**Dette et limites connues** :
- **Concurrence réservation / fermeture `[OPEN]`** : une réservation et une création de fermeture simultanées peuvent chacune passer leur vérification. Rare (staff). Remède : verrou consultatif par salon partagé. À traiter au plus tard avec la réservation en ligne (le trafic augmente).
- **Membre retiré et lien orphelin `[OPEN]`** : la ressource garde un `staffMembershipId` vers un membre supprimé ; le front l'affiche "membre retiré". Remède possible : hook `salon` → `resource` pour délier au retrait.
- **Rôle UI limité à la première page de salons `[OPEN]`** (20) ; remède : `role` dans la réponse de détail du salon, ou paginer la liste.
- **Agenda `[OPEN]`** : (a) positions en heure murale (décalage visuel la nuit d'un changement d'heure) ; (b) chargement N+1 (2 requêtes par ressource, 1 par prestation à l'ouverture du dialogue de réservation) → endpoint agrégé ; (c) le bloc revient brièvement à sa place après un dépôt (pas de mise à jour optimiste) ; (d) `touch-action: none` sur les blocs déplaçables, pas de déplacement au clavier dans la grille ; (e) liste de clients non paginée (la sélection n'en montre que 50) ; (f) vue Jour uniquement.
- **`AppointmentResponse` sans champs d'annulation `[OPEN]`** : `cancelledAt/By/reason` non exposés.
- **Profil du compte `[OPEN]`** : aucune modification du nom, de l'email ni du mot de passe dans l'UI.
- **Écrans hors ui-kit `[OPEN]`** : `HomeView`, `SalonDetailView`, `CreateSalonView`, authentification, `<input type="time">` de `ScheduleView` (une option "libellé masqué" du ui-kit les couvrirait).
- **Plages jusqu'à 24:00 `[OPEN]`** : impossible (limite `LocalTime`).
- **Annulation d'une invitation staff en attente `[OPEN]`** : `StaffInvitation.revoke()` existe, aucun endpoint.
- **CGU/consentement sur l'inscription via invitation `[OPEN]`** : mention absente d'`AcceptInvitationView`.
- **Gestion des secrets en production `[OPEN]`**, dette TypeScript `[OPEN]`, Spotless `[OPEN]`, géolocalisation du salon `[OPEN]`.

## 7. État d'avancement

**Sessions 1-12 : Phases 0 à 3 terminées.** Historique détaillé dans les versions antérieures de ce fichier ;
seules les décisions utiles sont conservées en §2, §4 et §9.

**Dernières tranches livrées (session 12)**
- **11 Clôture back** : JSON illisible → 400, `@IanaTimezone`, suppression des surcharges historiques de `ResourceManagementService`, `createCustomer` sans repli côté front.
- **9c** : catalogue migré vers le ui-kit (+ `UiCheckbox`, `UiButton size="sm"`).
- **12 Identité du compte** : prénom/nom (V1 modifiée en place), inscription, `/me`, `AccountSummary.displayName()`, liste du staff (nom d'affichage pour tous, email pour OWNER/MANAGER), front (inscription, invitation, store `auth` via `/me`, équipe, sélecteur de membre), `AcceptInvitationView` affiche ses erreurs.

**Enseignements récents**
1. `AppointmentSummary.status()` est une **`String`**.
2. `window` et les types DOM interdits dans le `<script>` des `.vue` (`no-undef`) → `globalThis`, inférence de types du template, `@click.self`.
3. **Erreur passée de Claude** : avoir conclu qu'une colonne n'existait pas (lien ressource↔staff) d'après l'entité/les DTO ; le schéma V1 la contenait → règle 17.
4. Un changement de constructeur ou de record casse les tests existants qui le construisent à la main : toujours lister les tests à adapter (règle 18). Pour un changement massif (43 appels `new Account(...)`), fournir un **script d'outillage testé sur des échantillons**, jetable et non committé.
5. `lint --fix` réordonne les attributs de template → donner la balise complète (règle 20).
6. Une `Response` simulée ne se lit qu'une fois : en test, en créer une neuve par appel réseau (un changement qui fait passer une action de 1 à 2 appels casse les tests qui réutilisent la même réponse).
7. Docker doit tourner pour les tests (`@SpringBootTest`) ; sinon les erreurs "Failed to load ApplicationContext" sont trompeuses (chercher le `Caused by`).
8. Bac à sable de Claude : le shell est `sh` (pas d'expansion d'accolades) et une commande est limitée à 300 s → écrire les fichiers un par un, découper les commandes.

**Prochaine étape** : Phase 4. Première discussion = découper la Phase 4 en tranches (§5), trancher les décisions de §6, donner la première branche.

## 8. Instructions pour Claude

1. Lire ce fichier en entier ; reprendre à §7 et §5.
2. Respecter les statuts : jamais un `[PROVISIONAL]` comme acté ; signaler les `[OPEN]` qui deviennent bloquants au lieu de trancher.
3. Ne pas re-proposer d'analyse ni redemander une décision `[DECIDED]`.
4. **Ne pas réclamer le gitingest** ; si un fichier précis est nécessaire, le demander.
5. En fin de tranche, mettre à jour ce fichier (statuts, §5, §6, §7, §9) — voir règle 21.
6. Claude n'a ni réseau, ni mvn, pnpm ou Docker : tout est validé par l'utilisateur, qui redonne les résultats bruts (`mvn clean verify`, `pnpm lint/typecheck/test/build`). Claude peut générer des fichiers et des zips dans son bac à sable et y vérifier par script (longueur de ligne notamment).
7. **Pour chaque tranche : donner le nom de branche AVANT d'écrire du code**, puis le message de commit une fois la tranche validée.
8. Avant de coder du Spring Boot 4.1, du JS/TS ou toute dépendance dont l'API a pu changer : **vérifier par recherche web**.
9. Tout contrôleur passe par la couche `application` (jamais un repository ni une entité du domaine).
10. Toute entité UUID implémente `Persistable<UUID>` avec `createdAt` en paramètre.
11. Avant un port technique transverse, vérifier `shared.domain`.
12. Rester strictement dans le scope de la tranche ; ne rien initier (design, fonctionnalité) sans proposition et accord.
13. **Fichiers déjà livrés puis modifiés par l'utilisateur** : ne JAMAIS renvoyer un fichier entier issu d'une copie interne de Claude (il écrase les corrections locales). Pour modifier un fichier existant : donner des **patchs** (avant/après). Pour une tranche : un zip ne contenant que les fichiers **nouveaux** ; les fichiers existants modifiés en patchs. Exceptions : un fichier que l'utilisateur vient de coller dans le message peut être renvoyé en entier ; le fichier de contexte est remplacé en entier.
14. Avant de livrer : **lignes ≤ 120 caractères** (par script sur le zip) et **imports inutilisés** (Checkstyle), fakes `IdGenerator` à ids distincts, stubs Mockito tous utilisés (ou `lenient()`).
15. Les valeurs de tests liées au temps utilisent des dates fixes (2099 = toujours futur, 2020 = toujours passé), jamais l'horloge réelle.
16. Pour une tranche à décisions ouvertes : présenter les hypothèses/choix par défaut, puis coder ; ne poser une question que si le défaut est risqué.
17. **Avant de conclure qu'une capacité "n'existe pas" dans le modèle, demander le schéma SQL (V1) ou l'entité.**
18. Quand une dépendance, un constructeur ou un record change, **lister les tests existants à adapter** avec le patch correspondant.
19. Front : `globalThis` (pas `window`), pas de type DOM dans le `<script>` d'un `.vue`, composants du ui-kit pour tout écran nouveau ou modifié, pas de dépendance sans recherche web, justification et accord.
20. Patchs de template Vue : donner la **balise complète** modifiée, jamais un diff d'attributs isolés.
21. **Fin de tranche — livrables, dans cet ordre** (une tranche par discussion pour garder un contexte léger) : (a) le **message de commit** ; (b) le **fichier de contexte complet mis à jour**, livré en pièce jointe (Claude ne peut pas modifier les project files : l'utilisateur remplace le fichier) ; (c) un **prompt de reprise** dans un bloc de code, prêt à coller dans une **nouvelle discussion**, pour lancer la tranche suivante. Le prompt de reprise contient : le rappel de lire le fichier de contexte (§7, §5, §6, §8) ; la **dernière tranche validée** (+ résultats verts) ; le **titre, la branche proposée et l'objectif** de la tranche suivante ; les **hypothèses/décisions** déjà arrêtées et les questions à trancher ; la **liste précise des fichiers** que l'utilisateur doit joindre (ou "aucun") ; les consignes de format de réponse (branche avant code, patchs avant/après, zip des fichiers nouveaux, ≤ 120 caractères, commit à la validation) ; et la demande de terminer à son tour par ces trois livrables. Si la tranche suivante n'est pas encore définie, le prompt demande de la définir en premier.
22. Garder ce fichier **léger** : à chaque mise à jour, condenser l'historique de §7 (ne garder que les décisions utiles en §2/§4/§9), supprimer les points ouverts résolus.
23. Bac à sable : shell `sh` (pas d'expansion d'accolades), commandes < 300 s ; tester tout script d'outillage sur des échantillons avant de le livrer.
24. Front : en test, simuler le réseau avec une `Response` neuve à chaque appel.

## 9. Annexe — carte des composants (signatures utiles)

### Guards (SpEL, par nom de bean, méthodes `public`, signatures primitives)
`@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)` · `@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)` · `@staffManagementGuard.canManageRole / canManageMembership / canChangeStaffRole / canViewStaffEmails(actorAccountId, salonId)`.

### Services applicatifs réutilisables
- `account.application` : `AccountQueryService.findAliveById(accountId): Optional<AccountSummary(accountId, email, emailVerified, firstName, lastName)>` (+ `displayName()`), `.findAliveAccountIdByEmail(email)` · `RegisterAccountService.register(RegisterAccountCommand(email, firstName, lastName, rawPassword))`.
- `resource.application` : `ResourceQueryService.findAliveBySalonId(salonId)`, `.findAliveInSalon(salonId, resourceId): Optional<ResourceSummary(resourceId, salonId, name, type, staffMembershipId, createdAt)>` · `ResourceManagementService.create(CreateResourceCommand(salonId, name, type), staffMembershipId)` / `.update(salonId, resourceId, name, type, staffMembershipId)` / `.delete` · `AvailabilityService.check(salonId, resourceId, Instant start, Instant end): AvailabilityCheck(boolean available, List<AvailabilityIssue> issues)` (issues : `OUTSIDE_SALON_HOURS, OUTSIDE_RESOURCE_HOURS, SALON_CLOSED, RESOURCE_CLOSED`) · `ClosureManagementService` · `ResourceDeletionCheck.hasBlockingAppointments(resourceId)` · `ClosureConflictCheck.findBlockingAppointments(salonId, resourceId|null, start, end)` · exceptions `ResourceNotFoundException`, `InvalidTimeRangeException(msg)`, `InvalidSalonTimezoneException`, `ClosureBlockedByAppointmentsException`, `InvalidResourceStaffLinkException`, `LinkedStaffMemberNotFoundException`, `StaffMemberAlreadyLinkedException`.
- `service.application` : `ServiceQueryService.findAliveInSalon(salonId, serviceId): Optional<ServiceSummary>`, `.resolveTerms(salonId, serviceId, resourceId): Optional<ServiceTerms(priceCents, durationMinutes)>` (vide si absent ou non associé) · `ServiceNotFoundException` · `ServiceDeletionCheck`.
- `customer.application` : `CustomerManagementService.create(CreateCustomerCommand(salonId, firstName, lastName, email, phone, internalNotes)): CustomerSummary`, `CustomerQueryService.findAliveInSalon(salonId, customerProfileId)`, `.findAliveBySalonId(salonId)` · `ContactRequiredException` → 400.
- `salon.application` : `SalonQueryService.findAliveById(salonId): Optional<SalonSummary(salonId, organizationId, name, address, postalCode, city, country, phone, timezone)>` · `SalonCreationService.createSalon(CreateSalonCommand)` · `StaffMembershipManagementService.listMembers(salonId): List<StaffMemberSummary(staffMembershipId, accountId, String role, Instant since)>`, `changeRole`, `removeMember` (soft-delete) · `StaffMemberQueryService.listMembers(salonId, actorAccountId): List<StaffMemberDetails(staffMembershipId, accountId, displayName, email, role, since)>`, `.existsAliveInSalon(salonId, staffMembershipId)`.
- `organization.application` : `OrganizationMembershipQueryService.isAliveOwner(accountId, organizationId)` · `OrganizationProvisioningService.ensureOrganizationForAccount(accountId)`.

### Domaine `appointment`
- `Appointment(id, salonId, customerProfileId, serviceId, startAt, endAt, priceAtBookingCents, durationAtBookingMinutes, createdAt)` → statut `CONFIRMED`. Mutateurs inconditionnels : `confirm()`, `cancel(Instant at, UUID by, String reason)`, `complete()`, `markNoShow()`, `reschedule(Instant startAt, Instant endAt)`.
- `AppointmentResource(appointmentId, resourceId)` (clé seule).
- Ports : `AppointmentRepository` (`save` = saveAndFlush, `findAliveById`, `existsActiveByServiceId`, `findOverlappingBySalonId(salonId, resourceId|null, from, to)`), `AppointmentResourceRepository` (`save`, `existsActiveByResourceId` [SQL natif], `findResourceIdsByAppointmentId`, `findByAppointmentIds`).
- Application : `AppointmentCreationService.create(CreateAppointmentCommand(salonId, customerProfileId, serviceId, resourceId, String startAt))` · `AppointmentRescheduleService.reschedule(salonId, appointmentId, String startAt)` · `AppointmentQueryService.findAliveInSalon(salonId, appointmentId)`, `.list(salonId, resourceId|null, String from|null, String to|null)` · `AppointmentStatusService.confirm/cancel(…, cancelledBy, reason)/complete/markNoShow(salonId, appointmentId)` · `AppointmentSummary(appointmentId, salonId, customerProfileId, serviceId, resourceId, Instant startAt, Instant endAt, **String** status, priceAtBookingCents, durationAtBookingMinutes, createdAt)` · adaptateurs `ResourceDeletionCheckAdapter`, `ServiceDeletionCheckAdapter`, `ClosureConflictCheckAdapter`.
- Exceptions → HTTP : `…NotFoundException` → 404 ; `ServiceResourceNotAssociatedException/InvalidTimeRangeException` → 400 ; `AppointmentNotAvailableException` (`issues()`) / `AppointmentConflictException` / `InvalidAppointmentTransitionException` / `AppointmentNotReschedulableException` → 409.

### Endpoints
- Auth/compte : `POST /api/auth/register` `{email, firstName, lastName, password}`, `/login`, `/refresh`, `/logout`, `/confirm-email`, `/forgot-password`, `/reset-password`, `/resend-verification-email` ; `GET /api/account/me` → `{accountId, email, firstName, lastName, emailVerified}` ; `POST /api/salons` (création, `timezone` validé) ; `GET /api/salons`, `GET /api/salons/{id}` ; invitations : `GET /api/staff-invitations/{token}`, `POST /api/staff-invitations/accept`.
- Sous `/api/salons/{salonId}` :
  - `resources` POST/GET, `resources/{id}` GET/PUT/DELETE (corps `{name, type, staffMembershipId|null}`), `resources/{rid}/schedule` GET/PUT, `resources/{rid}/closures` POST/GET + `/{id}` PUT/DELETE
  - `schedule` GET/PUT, `closures` POST/GET + `/{id}` PUT/DELETE (`{startAt, endAt, reason}`) ; 409 si RDV actifs recouverts
  - `services` POST/GET, `services/{id}` GET/PUT/DELETE, `services/{id}/resources` GET, `services/{id}/resources/{rid}` PUT/DELETE (`{overridePriceCents, overrideDurationMinutes}` nullables)
  - `customers` POST (201 `CustomerResponse`)/GET (tableau), `customers/{customerProfileId}` GET
  - `appointments` POST/GET(agenda), `appointments/{id}` GET, `appointments/{id}/confirm|cancel|complete|no-show|reschedule` PATCH (`cancel` : `{}` ou `{"reason":"…"}` ; `reschedule` : `{"startAt":"…"}`)
  - `staff` GET (`{staffMembershipId, accountId, displayName, email|null, role, since}`), `staff/{id}` PATCH/DELETE, `staff/invitations` GET/POST

### Front `portail-salon/src`
- `lib/` : `apiClient.ts` (`apiGet/apiPost/apiPut/apiPatch/apiDelete(path, payload?, accessToken)`, `ApiError(status, message, fieldErrors?, problem?)`), `requireToken.ts`, `errorMessage.ts`, `catalogFormat.ts`, `schedule.ts`, `zonedTime.ts` (`zonedLocalToInstant`, `instantToZonedLocal`, `formatInstant`, `isValidTimeZone`), `closureConflict.ts`, `agenda.ts`, `agendaDrag.ts`, `appointmentStatus.ts`, `appointmentActions.ts` (`availableActions`, `canReschedule`), `availabilityIssues.ts` (`describeIssues`).
- `stores/` : `auth` (`account: {accountId, email, firstName, lastName}`, `login`, `register(email, password, firstName, lastName)`, `loadAccount`, `restoreSession`), `salon`, `staff` (`members[]` avec `displayName`, `email|null`), `resource`, `salonService`, `schedule`, `closure` (`listOverlapping`), `customer` (`createCustomer`), `agenda` (`loadDay`), `appointment` (create/confirm/complete/markNoShow/cancel/reschedule).
- `composables/` : `useCatalogAccess.ts`, `useAppointmentDrag.ts`.
- `components/` : `catalog/ServiceLinksPanel.vue`, `agenda/{DayGrid, AppointmentCreateDialog, AppointmentDetailDialog, AppointmentRescheduleForm}.vue`, `AuthTextField.vue` ; `layouts/AuthLayout.vue`.
- `views/` : `catalog/{CatalogLayoutView, ResourcesView, ServicesView, ScheduleView, ClosuresView}.vue`, `AgendaView.vue`, `SalonDetailView.vue`, `CreateSalonView.vue`, `HomeView.vue`, `LoginView`, `RegisterView`, `AcceptInvitationView`, etc.

### Front `packages/ui-kit/src`
`index.ts` exporte `UiAlert, UiBadge, UiButton, UiCheckbox, UiDialog, UiInput, UiSelect, UiTextarea` ; `components/UiField.vue` (interne), `components/fieldStyles.ts`, `theme.css` (tokens).

### Conventions de tests (à respecter)
- ITs web : `@SpringBootTest(webEnvironment = MOCK, properties = "apontaja.security.rate-limiting.enabled=false") @AutoConfigureMockMvc @Import(PostgresTestcontainersConfiguration.class)` ; données créées via les repositories du domaine, assertions via `MockMvc` ; id extrait d'une réponse via `new ObjectMapper().readTree(...)`. Un `Account` de test : `new Account(UUID.randomUUID(), email, "Test", "User", "hash", Instant.now())`.
- Chaque nouveau domaine expose : test repository IT, tests unitaires des services (Mockito), IT contrôleur, et une **matrice d'autorisation** (OWNER d'organisation sans staff direct, cross-organisation, cross-salon même organisation, sans authentification, salon inexistant).
- États inatteignables par l'API publique (statut, `deletedAt`, `accountId`) : posés **par réflexion** dans le test, avec un commentaire qui le justifie.
- Concurrence : deux threads + `CountDownLatch`, statuts attendus `containsExactlyInAnyOrder(…)`.
- JsonPath sur une propriété absente ou nulle : passer par `ObjectMapper` (la forme de sérialisation des nulls n'est pas figée).
- Front : tests Vitest à côté de leur source ; composants du ui-kit testés avec `@vue/test-utils` (`<dialog>` simulé) ; logique pure (agenda, glisser) testée sans DOM ; réseau simulé avec une `Response` neuve par appel.
