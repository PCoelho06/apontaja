import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";

import { describeIssues } from "@/lib/availabilityIssues";

/**
 * Erreurs de l'agenda et des marcações en pt-PT.
 * Les messages du back sont en français : on s'appuie sur l'opération, le statut HTTP et
 * `issues[]` (409 d'indisponibilité), jamais sur le texte. Une erreur qui n'est pas une
 * `ApiError` (réseau, jeton absent) donne toujours le message générique de l'opération.
 */

const FORBIDDEN = "Não tem permissão para esta ação.";
const NO_LONGER_EXISTS = "Esta marcação já não existe.";
const UNAVAILABLE = "Este horário não está disponível.";

/** Message affiché quand un dépôt par glisser-déposer vise un créneau fermé (contrôle local). */
export const SLOT_NOT_OPEN =
  "Este horário não está aberto a marcações (horário de funcionamento ou encerramento).";

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

/** `issues` non vide : liste de motifs d'indisponibilité ; sinon `message` seul. */
export interface AppointmentFailure {
  issues: string[];
  message: string;
}

function failure(
  error: unknown,
  specific: ReadonlyMap<number, string>,
  generic: string,
): AppointmentFailure {
  const issues = describeIssues(error);
  if (issues.length > 0) {
    return { issues, message: UNAVAILABLE };
  }
  return { issues: [], message: byStatus(error, specific, generic) };
}

/** Lignes à afficher pour un échec : les motifs s'il y en a, sinon le message. */
export function failureLines(value: AppointmentFailure): string[] {
  return value.issues.length > 0 ? value.issues : [value.message];
}

/** Chargement d'une journée de l'agenda (ou des données de l'écran). */
export function describeAgendaLoadError(error: unknown): string {
  return byStatus(
    error,
    new Map([
      [403, "Não tem permissão para ver esta agenda."],
      [404, "Não foi possível aceder a este salão."],
    ]),
    "Não foi possível carregar a agenda.",
  );
}

/** Création d'une marcação. */
export function describeCreateError(error: unknown): AppointmentFailure {
  return failure(
    error,
    new Map([
      [400, "Verifique os dados da marcação."],
      [404, "O recurso, o serviço ou o cliente escolhido já não existe."],
      [
        409,
        "Já existe uma marcação neste horário. Atualize a agenda e escolha outro horário.",
      ],
    ]),
    "Não foi possível criar a marcação. Tente novamente.",
  );
}

/** Déplacement d'une marcação (changement d'heure). */
export function describeRescheduleError(error: unknown): AppointmentFailure {
  return failure(
    error,
    new Map([
      [400, "Hora inválida."],
      [404, NO_LONGER_EXISTS],
      [
        409,
        "Esta marcação já não pode ser alterada ou o horário foi ocupado entretanto. Atualize a agenda.",
      ],
    ]),
    "Não foi possível mudar a hora da marcação. Tente novamente.",
  );
}

export type StatusAction = "confirm" | "complete" | "no-show" | "cancel";

const STATUS_ACTION_MESSAGES: Record<StatusAction, string> = {
  confirm: "Não foi possível confirmar a marcação. Tente novamente.",
  complete: "Não foi possível concluir a marcação. Tente novamente.",
  "no-show": "Não foi possível registar a falta do cliente. Tente novamente.",
  cancel: "Não foi possível cancelar a marcação. Tente novamente.",
};

/** Confirmar, concluir, cliente faltou ou cancelar : 409 = transição já não possível. */
export function describeStatusActionError(
  error: unknown,
  action: StatusAction,
): string {
  return byStatus(
    error,
    new Map([
      [404, NO_LONGER_EXISTS],
      [409, "O estado desta marcação mudou. Atualize a agenda."],
    ]),
    STATUS_ACTION_MESSAGES[action],
  );
}

/** Vrai si l'agenda affichée est probablement périmée (marcação disparue ou déjà modifiée). */
export function shouldReloadAfter(error: unknown): boolean {
  return (
    error instanceof ApiError && (error.status === 404 || error.status === 409)
  );
}

/** Création d'un cliente à la volée dans le dialogue de marcação. */
export function describeNewCustomerError(error: unknown): string {
  if (error instanceof ApiError && error.status === 400) {
    return "Verifique os dados do cliente. Indique pelo menos um email ou um telefone.";
  }
  return byStatus(
    error,
    new Map(),
    "Não foi possível criar o cliente. Tente novamente.",
  );
}

/** Recherche de clients dans le dialogue de marcação. */
export function describeCustomerSearchError(error: unknown): string {
  return byStatus(
    error,
    new Map(),
    "Não foi possível pesquisar os clientes.",
  );
}

/**
 * Erreur de conversion locale d'une date/heure du salon (`zonedTime`) : ces messages sont
 * déjà en pt-PT. Toute autre valeur donne le message de repli.
 */
export function describeLocalTimeError(
  error: unknown,
  fallback = "Data e hora inválidas.",
): string {
  return error instanceof Error && !(error instanceof ApiError)
    ? error.message
    : fallback;
}
