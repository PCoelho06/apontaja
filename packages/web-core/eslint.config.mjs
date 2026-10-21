// @ts-check
import js from "@eslint/js";
import tseslint from "typescript-eslint";

// Paquet sans composant Vue : TypeScript + règles recommandées.
export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**", "coverage/**"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
);
