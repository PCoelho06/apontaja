// @ts-check
import js from "@eslint/js";
import tseslint from "typescript-eslint";

// Config minimale (Phase 0, étape 5) : lint TypeScript de base. Pas encore de plugin Vue actif
// (aucun composant .vue n'existe encore dans ui-kit) — eslint-plugin-vue est déjà en
// devDependency pour éviter une reconfiguration lors de l'ajout des premiers composants en
// Phase 1, mais volontairement pas encore branché dans cette config tant qu'il n'y a rien à
// lint côté .vue.
export default tseslint.config(js.configs.recommended, ...tseslint.configs.recommended, {
  ignores: ["dist/**"],
});
