import { ApiError } from "@apontaja/web-core/api-client";

import { describeIssues } from "@/lib/availabilityIssues";
import { resolveMessage, type MessageTable } from "@/lib/resolveError";

/**
 * Erreurs de l'agenda et des marcações en pt-PT.
 * Les messages du back sont en français : on s'appuie sur le `code` stable du back (5a-1), puis sur
 * l'opération, le statut HTTP et `issues[]` (409 d'indisponibilité), jamais sur le texte. Une erreur
 * qui n'est pas une `ApiError` (réseau, jeton absent) donne toujours le message générique.
 */

const NO_LONGER_EXISTS = "Esta marcação já não existe.";
const UNAVAILABLE = "Este horário não está disponível.";
const SLOT_TAKEN =
  "Já existe uma marcação neste horário. Atualize a agenda e escolha outro horário.";
const STATE_CHANGED = "O estado desta marcação mudou. Atualize a agenda.";

/** Message affiché quand un dépôt par glisser-déposer vise un créneau fermé (contrôle local). */
export const SLOT_NOT_OPEN =
  "Este horário não está aberto a marcações (horário de funcionamento ou encerramento).";

/** `issues` non vide : liste de motifs d'indisponibilité ; sinon `message` seul. */
export interface AppointmentFailure {
  issues: string[];
  message: string;
}

function failure(error: unknown, table: MessageTable): AppointmentFailure {
  const issues = describeIssues(error);
  if (issues.length > 0) {
    return { issues, message: UNAVAILABLE };
  }
  return { issues: [], message: resolveMessage(error, table) };
}

/** Lignes à afficher pour un échec : les motifs s'il y en a, sinon le message. */
export function failureLines(value: AppointmentFailure): string[] {
  return value.issues.length > 0 ? value.issues : [value.message];
}

/** Chargement d'une journée de l'agenda (ou des données de l'écran). */
export function describeAgendaLoadError(error: unknown): string {
  return resolveMessage(error, {
    statuses: new Map([
      [403, "Não tem permissão para ver esta agenda."],
      [404, "Não foi possível aceder a este salão."],
    ]),
    generic: "Não foi possível carregar a agenda.",
  });
}

/** Création d'une marcação. */
export function describeCreateError(error: unknown): AppointmentFailure {
  return failure(error, {
    codes: new Map([
      ["RESOURCE_NOT_FOUND", "O recurso escolhido já não existe."],
      ["SERVICE_NOT_FOUND", "O serviço escolhido já não existe."],
      ["CUSTOMER_NOT_FOUND", "O cliente escolhido já não existe ou foi anonimizado."],
      ["SERVICE_RESOURCE_NOT_ASSOCIATED", "Este recurso não realiza o serviço escolhido."],
      ["INVALID_TIME_RANGE", "Data e hora inválidas."],
      ["APPOINTMENT_NOT_AVAILABLE", UNAVAILABLE],
      ["APPOINTMENT_CONFLICT", SLOT_TAKEN],
    ]),
    statuses: new Map([
      [400, "Verifique os dados da marcação."],
      [404, "O recurso, o serviço ou o cliente escolhido já não existe."],
      [409, SLOT_TAKEN],
    ]),
    generic: "Não foi possível criar a marcação. Tente novamente.",
  });
}

/** Déplacement d'une marcação (changement d'heure). */
export function describeRescheduleError(error: unknown): AppointmentFailure {
  return failure(error, {
    codes: new Map([
      ["APPOINTMENT_NOT_FOUND", NO_LONGER_EXISTS],
      ["INVALID_TIME_RANGE", "Hora inválida."],
      [
        "APPOINTMENT_NOT_RESCHEDULABLE",
        "Esta marcação já não pode ser alterada. Atualize a agenda.",
      ],
      [
        "APPOINTMENT_CONFLICT",
        "O horário foi ocupado entretanto. Atualize a agenda e escolha outro horário.",
      ],
      ["APPOINTMENT_NOT_AVAILABLE", UNAVAILABLE],
    ]),
    statuses: new Map([
      [400, "Hora inválida."],
      [404, NO_LONGER_EXISTS],
      [
        409,
        "Esta marcação já não pode ser alterada ou o horário foi ocupado entretanto. Atualize a agenda.",
      ],
    ]),
    generic: "Não foi possível mudar a hora da marcação. Tente novamente.",
  });
}

export type StatusAction = "confirm" | "complete" | "no-show" | "cancel";

const STATUS_ACTION_MESSAGES: Record<StatusAction, string> = {
  confirm: "Não foi possível confirmar a marcação. Tente novamente.",
  complete: "Não foi possível concluir a marcação. Tente novamente.",
  "no-show": "Não foi possível registar a falta do cliente. Tente novamente.",
  cancel: "Não foi possível cancelar a marcação. Tente novamente.",
};

/** Confirmar, concluir, cliente faltou ou cancelar : transição interdite = estado mudou. */
export function describeStatusActionError(
  error: unknown,
  action: StatusAction,
): string {
  return resolveMessage(error, {
    codes: new Map([
      ["APPOINTMENT_NOT_FOUND", NO_LONGER_EXISTS],
      ["APPOINTMENT_TRANSITION_FORBIDDEN", STATE_CHANGED],
    ]),
    statuses: new Map([
      [404, NO_LONGER_EXISTS],
      [409, STATE_CHANGED],
    ]),
    generic: STATUS_ACTION_MESSAGES[action],
  });
}

/** Vrai si l'agenda affichée est probablement périmée (marcação disparue ou déjà modifiée). */
export function shouldReloadAfter(error: unknown): boolean {
  return (
    error instanceof ApiError && (error.status === 404 || error.status === 409)
  );
}

/** Création d'un cliente à la volée dans le dialogue de marcação. */
export function describeNewCustomerError(error: unknown): string {
  const contactRequired =
    "Verifique os dados do cliente. Indique pelo menos um email ou um telefone.";
  return resolveMessage(error, {
    codes: new Map([["CUSTOMER_CONTACT_REQUIRED", contactRequired]]),
    statuses: new Map([[400, contactRequired]]),
    generic: "Não foi possível criar o cliente. Tente novamente.",
  });
}

/** Recherche de clients dans le dialogue de marcação. */
export function describeCustomerSearchError(error: unknown): string {
  return resolveMessage(error, {
    generic: "Não foi possível pesquisar os clientes.",
  });
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
