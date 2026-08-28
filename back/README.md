# back/ — Apontaja API

Backend Spring Boot 4.1 (Spring Framework 7) / Java 21 / Maven.

> Décision initiale du fichier de contexte : "Spring Boot 3.x". La ligne 3.x étant passée EOL
> (30/06/2026), le projet démarre directement sur Spring Boot 4.1 — confirmé explicitement en
> session. Java 21 reste inchangé (compatible avec Spring Boot 4.1). Build tool : **Maven**
> (le fichier de contexte laissait le choix ouvert entre Gradle et Maven — tranché en session).

**État actuel (Phase 0, étape 7 terminée — Phase 0 complète)** :
- `pom.xml` : Spring Boot 4.1.1, dépendances Web / Security / Data JPA / Validation / Flyway
- `BackApplication` : point d'entrée, auto-configuration JPA/DataSource/Flyway **active** depuis
  l'étape 7 (plus d'exclusion temporaire)
- `GET /health` : répond `{"status":"UP"}`, seul endpoint public pour l'instant
- Sécurité minimale : tout le reste est refusé par défaut (`denyAll`), l'authentification réelle
  arrive en Phase 1
- CSRF désactivé au niveau bootstrap — **statut `[PROVISIONAL]`**, pas une décision actée, voir
  le commentaire dans `SecurityConfig`
- Structure de packages par domaine en place (§2 du fichier de contexte) : les 8 domaines
  métier (`account`, `organization`, `salon`, `resource`, `service`, `appointment`, `customer`,
  `audit`), chacun avec ses 4 sous-couches `web`/`application`/`domain`/`infrastructure`. Chaque
  package est documenté par un `package-info.java`. **Toujours aucune logique métier dedans**
  (arrive avec les vertical slices, à partir de la Phase 1).
- Deux packages transverses hors structure par domaine (`com.apontaja.back.web` pour `/health`,
  `com.apontaja.back.config` pour la sécurité) — **choix non explicitement acté**, documenté dans
  leurs `package-info.java`.
- **`ArchitectureTest`** (`src/test/java/com/apontaja/back/architecture/`) : vérifie
  mécaniquement le graphe de dépendances autorisées entre domaines et les règles de couches (§2).
  Module ArchUnit cœur (`com.tngtech.archunit:archunit:1.5.0`, pas `archunit-junit5`, voir
  commentaire dans `pom.xml`), piloté par `@TestFactory`/`@BeforeAll` JUnit Jupiter standard —
  génère un test dynamique par règle × domaine (~28 assertions au total). Ne couvre pas la
  discipline "DTO obligatoire" au sens strict, seulement son corollaire structurel (aucun accès
  direct aux entités JPA d'un autre domaine). Comme il n'y a encore aucune classe métier, ces
  règles passent trivialement pour l'instant.
- **Checkstyle** (`checkstyle.xml`) : ruleset custom volontairement resserré, enrichi de 4 règles
  supplémentaires par l'utilisateur (`UnusedLocalVariable`, `ParameterAssignment`,
  `RedundantModifier`, `UpperEll`). Lié à la phase `verify`. **Confirmé vert en CI.**
- **CI GitHub Actions** (`.github/workflows/ci.yml`, racine du repo) : job `back` = `mvn -B clean
  verify` (build, tests, ArchUnit, Checkstyle). **Confirmé vert.**
- **Gestion des secrets — dev local** : profil Spring `local`, `application-local.yml.example`
  commité, `application-local.yml` ignoré par Git. Secrets en **production** volontairement
  laissés `[OPEN]`, aucun hébergement choisi pour l'instant.
- **Flyway + PostgreSQL** (étape 7) : `apontaja-schema.sql` (racine de `back/`, référence
  canonique) dupliqué verbatim dans `src/main/resources/db/migration/V1__initial_schema.sql`
  (Flyway impose ce nom de fichier, impossible de le référencer directement — voir le bandeau en
  tête des deux fichiers pour la procédure de synchronisation, actuellement manuelle, aucune
  vérification automatique de dérive). `spring.jpa.hibernate.ddl-auto=validate` : Flyway est la
  seule source de vérité sur le schéma, Hibernate ne le modifie jamais.
- **Testcontainers** (`PostgreSQLContainer`, image `postgres:16-alpine` — pas `latest`, un bug
  connu de Flyway sous Spring Boot 4.0.x rejette PostgreSQL 18) : `BackApplicationTests` et
  `HealthControllerTest` bootent désormais un vrai PostgreSQL éphémère via
  `PostgresTestcontainersConfiguration` (`@ServiceConnection`), donc exercent réellement
  l'application de `V1__initial_schema.sql` par Flyway à chaque exécution — c'est la preuve de
  bon fonctionnement de la migration. **Nécessite Docker actif en local et en CI** (déjà présent
  par défaut sur les runners GitHub-hosted, aucune config CI supplémentaire nécessaire).
- **`docker-compose.yml`** (racine de `back/`) : PostgreSQL **de dev local uniquement**, distinct
  de Testcontainers — voir encadré ci-dessous, question posée en session après un premier retour
  d'expérience utilisateur (venant de H2, pas encore familier de Testcontainers).

### Testcontainers vs `docker-compose.yml` — ne pas confondre

Ce sont deux mécanismes séparés, pour deux besoins différents :

| | Testcontainers | `docker-compose.yml` |
|---|---|---|
| Pour quoi | Tests d'intégration (`mvn clean verify`) | Faire tourner l'appli toi-même (IDE, `mvn spring-boot:run`) |
| Démarré par | Le code du test lui-même (`PostgresTestcontainersConfiguration`), automatiquement | Toi, manuellement (`docker compose up -d`) |
| Durée de vie | Éphémère, un conteneur par exécution de tests, détruit ensuite | Persistant, tu le lances une fois et le réutilises |
| Fichier à écrire | Aucun — juste avoir Docker qui tourne | `back/docker-compose.yml` |

Remplace H2 dans ce projet précisément parce que le schéma utilise des fonctionnalités
PostgreSQL réelles (`citext`, contraintes `EXCLUDE` + `btree_gist`) que H2 ne simule pas
fidèlement même en mode compatibilité Postgres — cf. l'audit §3.4 : "Incohérence DB entre
environnements (MySQL dev / config Postgres prod)" dans l'ancien projet, qu'on évite ici en
testant contre un vrai PostgreSQL partout, dev comme tests.

**Deux pièges Spring Boot 4 identifiés et évités en amont** (recherchés avant d'écrire le code,
cette fois, plutôt que découverts après coup comme aux étapes précédentes) :
1. Flyway est passé en module séparé (`spring-boot-starter-flyway`, pas `flyway-core` en
   dépendance brute — sinon l'auto-configuration ne se déclenche jamais, silencieusement) +
   `flyway-database-postgresql` (support PostgreSQL externalisé depuis Flyway 10+).
2. PostgreSQL 18 pas encore supporté par Flyway tel que géré par Spring Boot 4.0.x → épinglage
   explicite sur `postgres:16-alpine` pour Testcontainers.

**⚠️ Toujours non exécuté par Claude** (pas d'accès réseau/`mvn`/Docker dans l'environnement de
génération) — Flyway + Testcontainers n'ont donc **jamais tourné réellement**, contrairement aux
étapes précédentes où au moins la compilation avait pu être anticipée. C'est le changement le
plus risqué de toute la Phase 0 à valider par ton prochain `mvn clean verify` (Docker actif
requis en local). Pour vérifier qu'`ArchitectureTest` détecterait bien une violation, on peut
temporairement ajouter une classe qui enfreint volontairement le graphe, relancer les tests,
constater l'échec, puis la supprimer.

Voir `../APONTAJA-RESTART-CONTEXT.md` pour toutes les décisions d'architecture (structure de
packages, graphe de dépendances entre domaines, conventions).

## Gestion des secrets (Phase 0, étape 6)

**Portée volontairement limitée au dev local** : aucun hébergement de production n'est encore
choisi (confirmé explicitement en session), donc pas de stratégie de secrets manager cloud
(Vault, AWS Secrets Manager...) mise en place pour l'instant — ce serait prématuré et
potentiellement à refaire selon l'hébergement retenu. **`[OPEN]`** : gestion des secrets en
production, à trancher quand l'hébergement le sera (voir `APONTAJA-RESTART-CONTEXT.md`).

**Mécanisme en place pour le dev local** (profil Spring `local`) :
- `application.yml` : config par défaut, commitée, **jamais de secret dedans**
- `application-local.yml.example` : template commité, documente les clés attendues avec des
  valeurs factices
- `application-local.yml` : ta copie réelle, avec les vraies valeurs — **déjà exclue de Git**
  (voir `.gitignore` racine, en place depuis l'étape 1)
- Activation : variable d'environnement `SPRING_PROFILES_ACTIVE=local` (jamais en dur dans un
  fichier commité)

Depuis l'étape 7, `application-local.yml` contient les vrais identifiants PostgreSQL locaux
(datasource réellement branché dans `BackApplication`) — c'est le premier secret concret que ce
mécanisme protège.

## Historique des corrections post-génération

Chaque étape a été écrite sans accès réseau ni `mvn` côté Claude ; les problèmes ci-dessous ont
été trouvés et corrigés via un vrai `mvn clean verify` exécuté par toi.

**Étape 2, 1er correctif** — Spring Boot 4 a éclaté l'ancien jar monolithique
`spring-boot-autoconfigure` en modules dédiés (nov. 2025, postérieur à la connaissance de
Claude), renommant les packages de plusieurs classes d'auto-configuration :
- `DataSourceAutoConfiguration` et `DataSourceTransactionManagerAutoConfiguration` :
  `org.springframework.boot.autoconfigure.jdbc` → `org.springframework.boot.jdbc.autoconfigure`
- `HibernateJpaAutoConfiguration` :
  `org.springframework.boot.autoconfigure.orm.jpa` → `org.springframework.boot.hibernate.autoconfigure`

**Étape 2, 2e correctif** :
- `AutoConfigureMockMvc` a migré vers `org.springframework.boot.webmvc.test.autoconfigure`, et
  nécessite le starter de test dédié `spring-boot-starter-webmvc-test`
- `spring-boot-starter-web` renommée en `spring-boot-starter-webmvc` (l'ancien nom compile encore
  mais est officiellement déprécié en Spring Boot 4)

Wrapper Maven généré avec succès après ces deux correctifs (`mvn -N wrapper:wrapper -Dmaven=3.9.9`).

**Étape 5** — deux catégories de bugs distinctes, corrigées par l'utilisateur :
- *Placement erroné dans `checkstyle.xml`* : `LineLength` avait été placé sous `TreeWalker` alors
  qu'il doit être un enfant direct de `Checker` (il travaille sur les lignes brutes du fichier,
  pas sur l'AST — contrairement à la majorité des autres checks). Vrai bug de conception de ma
  part, pas une histoire de version.
- *Versions front pariées à l'aveugle* : `pnpm` (proposé `9.12.0`, en réalité déjà en `11.24.0`
  au moment de l'exécution), `eslint`/`@eslint/js`/`eslint-plugin-vue` (proposés en version 9.x,
  réellement en version 10.x). Sans réseau, je ne pouvais que deviner des numéros plausibles au
  moment de la rédaction — corrigés par l'utilisateur avec les versions réellement installées.
- *Node 20 incompatible avec pnpm 11* : la première exécution de CI a échoué côté front pour
  cette raison ; `.nvmrc` corrigé de `20` à `22`.

Les deux pipelines (back et front) sont passées avec succès après ces correctifs.
