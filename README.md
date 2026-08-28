# Apontaja

SaaS de gestion de rendez-vous pour salons (coiffure/beauté). Reboot du projet historique
"Marquei" (Symfony/Vue → Spring Boot/Vue), voir `APONTAJA-RESTART-CONTEXT.md` pour l'historique
complet des décisions, l'audit de l'ancien projet, le modèle de données et la roadmap.

## Structure du mono-repo

```
apontaja/
├── back/                     # Backend Spring Boot 3.x / Java 21 (Gradle, module unique)
├── portail-salon/            # Frontend Vue 3 — écrans staff (login, agenda, carnet client...)
├── portail-client/           # Frontend Vue 3 — inscription, réservation, historique
├── packages/
│   └── ui-kit/                # Design system partagé entre les deux portails
├── pnpm-workspace.yaml        # Workspace pnpm (portail-salon, portail-client, packages/*)
└── package.json                # Scripts racine (dev, build, lint, test sur tout le workspace)
```

`back/` n'est **pas** géré par pnpm (c'est un projet Gradle indépendant) mais vit dans le même
repo pour permettre le développement en tranches verticales (backend + frontend d'une
fonctionnalité ensemble).

## Prérequis

- Node.js 22+ (voir `.nvmrc`)
- pnpm 11+ (`corepack enable` recommandé)
- Java 21
- **Docker actif** — requis pour deux usages distincts : PostgreSQL en dev local (voir
  `back/src/main/resources/application-local.yml.example`), et Testcontainers pour les tests
  d'intégration back (`mvn clean verify` démarre son propre PostgreSQL éphémère automatiquement,
  aucune action manuelle nécessaire pour les tests — mais Docker doit tourner)

## CI/CD (GitHub Actions)

`.github/workflows/ci.yml` — deux jobs indépendants, déclenchés sur chaque PR (+ push sur `main`,
+ déclenchement manuel) :
- **back** : `mvn -B clean verify` (build, tests unitaires/intégration, y compris `ArchitectureTest`)
- **front** : `pnpm install --frozen-lockfile` puis lint/build/test sur tous les packages du
  workspace (`--if-present`, tant que `portail-salon`/`portail-client` restent des placeholders)

**⚠️ Deux préalables avant que la CI puisse passer, ni l'un ni l'autre encore fait par Claude** :
1. **`pnpm-lock.yaml` doit exister et être commité** — je n'ai ni réseau ni `pnpm` dans mon
   environnement de génération, donc je n'ai jamais pu exécuter `pnpm install` réellement. Lance-le
   une fois en local à la racine du repo (ça validera aussi que tous les `package.json` sont
   syntaxiquement corrects) et commite le lockfile généré.
2. **Le wrapper Maven (`back/mvnw`, `back/mvnw.cmd`, `back/.mvn/`)**, que tu as généré en local à
   l'étape 2, n'a jamais transité par moi (généré directement sur ta machine) donc n'est pas dans
   les archives que je te donne — assure-toi qu'il est bien ajouté au repo Git de ton côté. Ce
   n'est pas bloquant pour la CI telle qu'écrite (elle utilise le `mvn` pré-installé sur le runner
   GitHub-hosted, pas `./mvnw`), mais vaut la peine d'être vérifié pour la reproductibilité en
   local.

**Lint côté back (Java)** : Checkstyle, tranché explicitement en session (voir
`back/README.md` et `back/checkstyle.xml` pour le détail du ruleset et sa justification). Lié à
la phase `verify` Maven, donc déjà couvert par le job `back` de la CI sans modification du
workflow.

**Lint côté front (TypeScript)** : seul `packages/ui-kit` a un vrai lint (ESLint 9 flat config +
`typescript-eslint`, non vérifié faute de `pnpm install` réel — voir point 1 ci-dessus).
`portail-salon`/`portail-client` n'ont pas encore de script `lint` (packages encore vides), ce qui
sera mis en place avec leur initialisation Vite/Vue réelle en Phase 1.

## Démarrage

À compléter au fur et à mesure de la Phase 0 (voir `APONTAJA-RESTART-CONTEXT.md`, §5).

## État du projet

Voir `APONTAJA-RESTART-CONTEXT.md` — section "État d'avancement" pour le suivi de session à
session, et section "Plan d'action" pour la roadmap complète.
