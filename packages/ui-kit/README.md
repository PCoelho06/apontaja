# @apontaja/ui-kit

Design system partagé entre `portail-salon` et `portail-client`.

**Décision actée** : repart entièrement de zéro, aucun portage automatique des composants
`Coelho*` de l'ancien projet. Récupération ponctuelle possible si un besoin précis se présente
en cours de route, au cas par cas.

**État** : squelette de package uniquement (Phase 0, étape 1), `src/index.ts` vide. Les premiers
composants seront ajoutés au fil des besoins réels des vertical slices, en commençant par
l'authentification (Phase 1).

**Lint** (Phase 0, étape 5) : ESLint 9 (flat config) + `typescript-eslint`, `eslint-plugin-vue`
en devDependency mais pas encore branché dans la config (aucun composant `.vue` pour l'instant).
`pnpm lint` depuis ce dossier, ou `pnpm --filter @apontaja/ui-kit lint` depuis la racine. Pas de
`tsconfig.json` pour l'instant — pas nécessaire pour le lint de base, à ajouter avec le premier
vrai build TypeScript en Phase 1.
