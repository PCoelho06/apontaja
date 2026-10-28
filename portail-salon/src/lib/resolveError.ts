import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";

/**
 * Résolution commune des messages d'erreur du portail salon (pt-PT). Les messages du back sont en
 * français : on s'appuie sur le `code` stable (5a-1), puis sur le statut HTTP, jamais sur le texte.
 */

export const FORBIDDEN = "Não tem permissão para esta ação.";

export interface MessageTable {
  /** `code` stable du back -> message. Une `Map` : une clé arbitraire n'atteint jamais un prototype. */
  codes?: ReadonlyMap<string, string>;
  /** Repli par statut HTTP quand le code est absent ou inconnu. */
  statuses?: ReadonlyMap<number, string>;
  /** Message pour toute autre erreur (réseau, jeton absent, statut non listé). */
  generic: string;
}

/**
 * Ordre : pas une `ApiError` -> générique ; 429 -> message commun ; code connu -> son message ;
 * 403 -> message de la table ou « sem permissão » ; statut listé -> son message ; sinon générique.
 */
export function resolveMessage(error: unknown, table: MessageTable): string {
  if (!(error instanceof ApiError)) {
    return table.generic;
  }
  if (error.status === 429) {
    return TOO_MANY_ATTEMPTS;
  }
  const code = error.code;
  if (code !== undefined) {
    const byCode = table.codes?.get(code);
    if (byCode !== undefined) {
      return byCode;
    }
  }
  if (error.status === 403) {
    return table.statuses?.get(403) ?? FORBIDDEN;
  }
  return table.statuses?.get(error.status) ?? table.generic;
}

/** Ne garde que les champs connus (une clé arbitraire du back n'atteint jamais un prototype). */
export function translateFields(
  error: unknown,
  table: ReadonlyMap<string, string>,
): Record<string, string> {
  const fieldErrors: Record<string, string> = {};
  if (error instanceof ApiError) {
    for (const field of Object.keys(error.fieldErrors ?? {})) {
      const message = table.get(field);
      if (message !== undefined) {
        fieldErrors[field] = message;
      }
    }
  }
  return fieldErrors;
}
