import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";

/**
 * Erreurs de l'écran « Pedidos por validar » en pt-PT. Jamais le message du back (français) :
 * opération + statut HTTP ; une erreur qui n'est pas une `ApiError` donne le message générique.
 */

const FORBIDDEN = "Não tem permissão para esta ação.";
const CHANGED =
  "Este pedido mudou entretanto (cancelado pelo cliente ou já tratado). A lista foi atualizada.";

function byStatus(
  error: unknown,
  specific: ReadonlyMap<number, string>,
  generic: string,
): string {
  if (!(error instanceof ApiError)) {
    return generic;
  }
  if (error.status === 429) {
    return TOO_MANY_ATTEMPTS;
  }
  if (error.status === 403) {
    return specific.get(403) ?? FORBIDDEN;
  }
  return specific.get(error.status) ?? generic;
}

/** Chargement initial (`load`) ou actualisation manuelle (`refresh`). */
export function describeRequestsLoadError(
  error: unknown,
  operation: "load" | "refresh",
): string {
  return byStatus(
    error,
    new Map([
      [403, "Não tem permissão para ver os pedidos deste salão."],
      [404, "Não foi possível aceder a este salão."],
    ]),
    operation === "load"
      ? "Não foi possível carregar os pedidos."
      : "Não foi possível atualizar os pedidos.",
  );
}

/** Confirmation ou refus d'une demande. */
export function describeRequestActionError(
  error: unknown,
  action: "confirm" | "decline",
): string {
  return byStatus(
    error,
    new Map([
      [404, CHANGED],
      [409, CHANGED],
    ]),
    action === "confirm"
      ? "Não foi possível confirmar o pedido."
      : "Não foi possível recusar o pedido.",
  );
}

/** 404/409 : la demande a changé, la vue recharge la liste. */
export function shouldReloadRequests(error: unknown): boolean {
  return (
    error instanceof ApiError && (error.status === 404 || error.status === 409)
  );
}
