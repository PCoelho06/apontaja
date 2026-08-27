# back/ — Apontaja API

Backend Spring Boot 4.1 (Spring Framework 7) / Java 21 / Maven.

> Décision initiale du fichier de contexte : "Spring Boot 3.x". La ligne 3.x étant passée EOL
> (30/06/2026), le projet démarre directement sur Spring Boot 4.1 — confirmé explicitement en
> session. Java 21 reste inchangé (compatible avec Spring Boot 4.1). Build tool : **Maven**
> (le fichier de contexte laissait le choix ouvert entre Gradle et Maven — tranché en session).

**État actuel (Phase 0, étape 6 terminée)** :
- `pom.xml` : Spring Boot 4.1.1, dépendances Web / Security / Data JPA / Validation
- `BackApplication` : point d'entrée, avec exclusion **temporaire** de l'auto-configuration
  JPA/DataSource (aucune base de données configurée avant l'étape 7 — voir TODO dans le code)
- `GET /health` : répond `{"status":"UP"}`, seul endpoint public pour l'instant
- Sécurité minimale : tout le reste est refusé par défaut (`denyAll`), l'authentification réelle
  arrive en Phase 1
- CSRF désactivé au niveau bootstrap — **statut `[PROVISIONAL]`**, pas une décision actée, voir
  le commentaire dans `SecurityConfig`
- Tests : `BackApplicationTests` (démarrage du contexte) + `HealthControllerTest` (MockMvc)
- Structure de packages par domaine en place (§2 du fichier de contexte) : les 8 domaines
  métier (`account`, `organization`, `salon`, `resource`, `service`, `appointment`, `customer`,
  `audit`), chacun avec ses 4 sous-couches `web`/`application`/`domain`/`infrastructure`. Chaque
  package est documenté par un `package-info.java`. **Toujours aucune logique métier dedans.**
- Deux packages transverses hors structure par domaine (`com.apontaja.back.web` pour `/health`,
  `com.apontaja.back.config` pour la sécurité) — **choix non explicitement acté**, documenté dans
  leurs `package-info.java`.
- **`ArchitectureTest`** (`src/test/java/com/apontaja/back/architecture/`) : vérifie
  mécaniquement le graphe de dépendances autorisées entre domaines et les règles de couches (§2).
  Module ArchUnit cœur (`com.tngtech.archunit:archunit:1.5.0`, pas `archunit-junit5`, voir
  commentaire dans `pom.xml`), piloté par `@TestFactory`/`@BeforeAll` JUnit Jupiter standard —
  génère un test dynamique par règle × domaine (~28 assertions au total). Couvre : isolation des
  couches au sein d'un domaine, confidentialité inter-domaines (seule `.application` est
  accessible depuis l'extérieur d'un domaine), et le graphe littéral du §2. **Ne couvre pas** la
  discipline "DTO obligatoire" au sens strict (vérifier qu'une classe exposée est bien un DTO) —
  seulement son corollaire structurel (aucun accès direct aux entités JPA d'un autre domaine).
  Comme il n'y a encore aucune classe métier, ces règles passent trivialement pour l'instant.
- **Checkstyle** (`checkstyle.xml`, décidé explicitement en session — voir `[OPEN]` fermé dans
  `APONTAJA-RESTART-CONTEXT.md`) : ruleset custom volontairement resserré (imports propres, pas
  de code mort évident, style d'accolades, 120 caractères/ligne) plutôt que `google_checks.xml`
  (imposerait une indentation 2 espaces, en contradiction avec `.editorconfig`) ou
  `sun_checks.xml` complet (module `Indentation` historiquement capricieux, Javadoc obligatoire
  qui aurait fait échouer tout le code déjà écrit). Lié à la phase `verify` via
  `maven-checkstyle-plugin` 3.6.0 : `mvn clean verify` (déjà utilisé partout, y compris en CI)
  suffit à le déclencher, aucun changement nécessaire dans `.github/workflows/ci.yml`.
  **Confirmé vert en CI** après correction d'un vrai bug de ma part (voir "Historique des
  corrections" ci-dessous) et enrichi de 4 règles supplémentaires par l'utilisateur
  (`UnusedLocalVariable`, `ParameterAssignment`, `RedundantModifier`, `UpperEll`).
- **CI GitHub Actions** (`.github/workflows/ci.yml`, racine du repo) : job `back` = `mvn -B clean
  verify` (build, tests, ArchUnit, Checkstyle, tout en une commande). **Confirmé vert** après
  correctifs (voir "Historique des corrections").
- **Gestion des secrets — dev local** (voir section dédiée ci-dessous) : mécanisme en place
  (profil Spring `local`, `application-local.yml.example` commité, `application-local.yml`
  ignoré par Git). Gestion des secrets en **production** volontairement laissée `[OPEN]`, aucun
  hébergement choisi pour l'instant.

**⚠️ `ArchitectureTest` n'a pas été exécuté par Claude** (pas d'accès réseau/`mvn` dans
l'environnement de génération) — à valider avec le prochain `mvn clean verify`. Pour vérifier que
les règles détecteraient bien une violation (et ne sont pas vacuousement toujours vraies faute de
code métier), on peut temporairement ajouter une classe qui enfreint volontairement le graphe
(ex. une classe dans `account.application` qui référence `salon.domain`), relancer les tests,
constater l'échec attendu, puis la supprimer.

`apontaja-schema.sql` (à la racine de ce dossier) est le schéma PostgreSQL de référence —
il fait foi en cas de divergence avec la documentation. Il sera appliqué via Flyway comme
première migration (étape 7 de la Phase 0).

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

Pas encore de secret réel à gérer à ce stade (le datasource n'est pas encore branché — voir TODO
dans `BackApplication.java`), donc `application-local.yml.example` ne contient qu'un espace
réservé pour les identifiants PostgreSQL qui seront finalisés à l'étape 7.

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
