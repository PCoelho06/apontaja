import { ApiError } from "@apontaja/web-core/api-client";

/**
 * Erreurs de « mes rendez-vous » traduites en portugais du Portugal. Les messages du back sont en
 * français : on s'appuie sur la propriété `code` (ou le statut HTTP), jamais sur le texte.
 */
export type CancelFailureKind =
  | "CANCELLATION_NOT_ALLOWED"
  | "CANCELLATION_DEADLINE_PASSED"
  | "NOT_FOUND"
  | "RATE_LIMITED"
  | "UNAUTHENTICATED"
  | "UNKNOWN";

export interface CancelFailure {
  kind: CancelFailureKind;
  message: string;
}

const MESSAGES: Record<CancelFailureKind, string> = {
  CANCELLATION_NOT_ALLOWED: "Esta marcação já não pode ser cancelada.",
  CANCELLATION_DEADLINE_PASSED:
    "O prazo de cancelamento terminou. Contacte o salão.",
  NOT_FOUND: "Marcação não encontrada.",
  RATE_LIMITED: "Demasiados pedidos. Aguarde um momento e tente novamente.",
  UNAUTHENTICATED: "A sua sessão expirou. Inicie sessão novamente.",
  UNKNOWN: "Não foi possível cancelar a marcação. Tente novamente.",
};

function failure(kind: CancelFailureKind): CancelFailure {
  return { kind, message: MESSAGES[kind] };
}

export function describeCancelError(error: unknown): CancelFailure {
  if (!(error instanceof ApiError)) {
    return failure("UNKNOWN");
  }
  const code: unknown = error.problem?.code;
  switch (code) {
    case "CANCELLATION_NOT_ALLOWED":
    case "CANCELLATION_DEADLINE_PASSED":
      return failure(code);
    default:
      break;
  }
  switch (error.status) {
    case 401:
      return failure("UNAUTHENTICATED");
    case 404:
      return failure("NOT_FOUND");
    case 429:
      return failure("RATE_LIMITED");
    default:
      return failure("UNKNOWN");
  }
}

/**
 * Vrai quand l'état affiché est périmé (rendez-vous déjà annulé, délai dépassé, introuvable) :
 * l'écran recharge la liste au lieu de laisser le client réessayer.
 */
export function isStaleView(kind: CancelFailureKind): boolean {
  return (
    kind === "CANCELLATION_NOT_ALLOWED" ||
    kind === "CANCELLATION_DEADLINE_PASSED" ||
    kind === "NOT_FOUND"
  );
}

/** Message d'une erreur de chargement de la liste. */
export function describeListError(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    return MESSAGES.RATE_LIMITED;
  }
  return "Não foi possível carregar as suas marcações. Tente novamente.";
}
