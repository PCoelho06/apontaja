# @apontaja/web-core

Socle front partagé entre `portail-salon` et `portail-client`, consommé **en source** (comme le ui-kit).

| Sous-chemin | Contenu |
|---|---|
| `@apontaja/web-core/api-client` | `ApiError`, `apiGet/Post/Put/Patch/Delete`, `apiPostWithCsrf`, `configureAuthClient` |
| `@apontaja/web-core/require-token` | `requireToken()` : jeton du client configuré, sinon `Error("Non authentifié.")` |
| `@apontaja/web-core/error-message` | `errorMessage(error, fallback)` |
| `@apontaja/web-core/auth` | `defineAuthStore({ portal: "SALON" \| "CLIENT" })` |

Chaque application instancie le store dans son propre `src/stores/auth.ts` :

```ts
import { defineAuthStore } from "@apontaja/web-core/auth";

export const useAuthStore = defineAuthStore({ portal: "SALON" });
```

`defineAuthStore` configure aussi le client API (jeton, rafraîchissement) : il doit donc être importé
avant le premier appel réseau authentifié (c'est le cas via `main.ts`).

`vue` et `pinia` sont des peerDependencies : aucune autre dépendance d'exécution.
