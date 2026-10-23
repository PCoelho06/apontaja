/**
 * Destination de retour après connexion : uniquement un chemin interne (jamais une URL absolue ni
 * `//hote`). Toute autre valeur donne `null`.
 */
export function safeRedirect(value: unknown): string | null {
  if (typeof value !== "string" || value === "") {
    return null;
  }
  if (!value.startsWith("/") || value.startsWith("//")) {
    return null;
  }
  if (value.includes("\\")) {
    return null;
  }
  for (const character of value) {
    if (character.charCodeAt(0) < 32) {
      return null;
    }
  }
  return value;
}
