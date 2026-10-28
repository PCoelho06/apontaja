import { ApiError } from "@apontaja/web-core/api-client";

import { resolveMessage } from "@/lib/resolveError";

/**
 * Erreurs de l'écran « Pedidos por validar » en pt-PT. Jamais le message du back (français) :
 * `code` stable (5a-1), puis opération + statut HTTP ; une erreur qui n'est pas une `ApiError`
 * donne le message générique.
 */

const CHANGED =
  "Este pedido mudou entretanto (cancelado pelo cliente ou já tratado). A lista foi atualizada.";

/** Chargement initial (`load`) ou actualisation manuelle (`refresh`). */
export function describeRequestsLoadError(
  error: unknown,
  operation: "load" | "refresh",
): string {
  return resolveMessage(error, {
    statuses: new Map([
      [403, "Não tem permissão para ver os pedidos deste salão."],
      [404, "Não foi possível aceder a este salão."],
    ]),
    generic:
      operation === "load"
        ? "Não foi possível carregar os pedidos."
        : "Não foi possível atualizar os pedidos.",
  });
}

/** Confirmation ou refus d'une demande. */
export function describeRequestActionError(
  error: unknown,
  action: "confirm" | "decline",
): string {
  return resolveMessage(error, {
    codes: new Map([
      ["APPOINTMENT_NOT_FOUND", CHANGED],
      ["APPOINTMENT_TRANSITION_FORBIDDEN", CHANGED],
    ]),
    statuses: new Map([
      [404, CHANGED],
      [409, CHANGED],
    ]),
    generic:
      action === "confirm"
        ? "Não foi possível confirmar o pedido."
        : "Não foi possível recusar o pedido.",
  });
}

/** 404/409 : la demande a changé, la vue recharge la liste. */
export function shouldReloadRequests(error: unknown): boolean {
  return (
    error instanceof ApiError && (error.status === 404 || error.status === 409)
  );
}
