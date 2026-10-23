import { ApiError } from "@apontaja/web-core/api-client";

/**
 * Erreurs de la réservation en ligne traduites en portugais du Portugal. Les messages du back sont en
 * français : on s'appuie sur la propriété `code` (ou le statut HTTP), jamais sur le texte.
 */
export type BookingFailureKind =
  | "EMAIL_NOT_VERIFIED"
  | "SHARING_CONSENT_REQUIRED"
  | "BOOKING_QUOTA_EXCEEDED"
  | "SLOT_UNAVAILABLE"
  | "NOT_FOUND"
  | "RATE_LIMITED"
  | "UNAUTHENTICATED"
  | "INVALID"
  | "UNKNOWN";

export interface BookingFailure {
  kind: BookingFailureKind;
  message: string;
}

const MESSAGES: Record<BookingFailureKind, string> = {
  EMAIL_NOT_VERIFIED: "Confirme o seu endereço de email antes de marcar.",
  SHARING_CONSENT_REQUIRED:
    "Para a primeira marcação neste salão, tem de autorizar a partilha dos seus dados de contacto.",
  BOOKING_QUOTA_EXCEEDED:
    "Atingiu o número máximo de marcações futuras neste salão. Cancele uma marcação ou aguarde que se realize.",
  SLOT_UNAVAILABLE: "Este horário já não está disponível. Escolha outro.",
  NOT_FOUND: "Este salão não aceita marcações online neste momento.",
  RATE_LIMITED: "Demasiados pedidos. Aguarde um momento e tente novamente.",
  UNAUTHENTICATED: "A sua sessão expirou. Inicie sessão novamente.",
  INVALID:
    "Os dados da marcação não são válidos. Verifique o telemóvel e tente novamente.",
  UNKNOWN: "Não foi possível concluir a marcação. Tente novamente.",
};

function failure(kind: BookingFailureKind): BookingFailure {
  return { kind, message: MESSAGES[kind] };
}

export function describeBookingError(error: unknown): BookingFailure {
  if (!(error instanceof ApiError)) {
    return failure("UNKNOWN");
  }
  const code: unknown = error.problem?.code;
  switch (code) {
    case "EMAIL_NOT_VERIFIED":
    case "SHARING_CONSENT_REQUIRED":
    case "BOOKING_QUOTA_EXCEEDED":
    case "SLOT_UNAVAILABLE":
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
    case 400:
      return failure("INVALID");
    default:
      return failure("UNKNOWN");
  }
}

/** Message d'une erreur de chargement (salon, prestations, créneaux). */
export function describeLoadError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) {
      return MESSAGES.NOT_FOUND;
    }
    if (error.status === 429) {
      return MESSAGES.RATE_LIMITED;
    }
  }
  return "Não foi possível carregar os dados. Tente novamente.";
}
