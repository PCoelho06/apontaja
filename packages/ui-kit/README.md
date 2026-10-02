# @apontaja/ui-kit

Design system partagé entre `portail-salon` et `portail-client`.

**Décision actée** : repart de zéro, aucun portage des composants `Coelho*` de l'ancien projet.

## Utilisation

Paquet consommé **en source** (pas d'étape de build). Dans une application :

- `package.json` : `"@apontaja/ui-kit": "workspace:*"`
- CSS d'entrée : `@import "@apontaja/ui-kit/theme.css";` et `@source "<chemin relatif>/packages/ui-kit/src";`
  (Tailwind 4 ne scanne que le dossier du CSS d'entrée : sans `@source`, les classes des composants
  ne sont pas générées)
- `import { UiButton } from "@apontaja/ui-kit"`

## Composants

`UiButton` (primaire, secondaire, danger, chargement) · `UiInput`, `UiSelect`, `UiTextarea`
(libellé, aide, erreur, `v-model`) · `UiAlert` · `UiBadge` · `UiDialog` (`<dialog>` natif,
`v-model:open`). Un composant n'est ajouté que lorsqu'un écran en a besoin.

## Règles

- **Tokens uniquement** : les composants n'utilisent que les couleurs et polices de `src/theme.css`,
  jamais de couleur en dur. La reprise du design se fait dans ce fichier.
- `UiDialog` : mettre `autofocus` sur le premier champ pour le focaliser à l'ouverture ; le corps
  n'est rendu que lorsque la boîte est ouverte (un formulaire repart de zéro).
- Pas de globales navigateur (`window`, `document`) dans les `.vue` (règle ESLint `no-undef`).

## Commandes

`pnpm --filter @apontaja/ui-kit lint | typecheck | test`, ou `pnpm -r …` depuis la racine.
