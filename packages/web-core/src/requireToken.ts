import { getConfiguredAccessToken } from "./apiClient";

/** Jeton d'accès courant ; lève une erreur si la session n'est pas authentifiée. */
export function requireToken(): string {
  const token = getConfiguredAccessToken();
  if (!token) {
    throw new Error("Non authentifié.");
  }
  return token;
}
