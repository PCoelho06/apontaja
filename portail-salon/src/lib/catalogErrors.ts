import type { FormFailure } from "@apontaja/web-core/auth-errors";

import { resolveMessage, translateFields } from "@/lib/resolveError";

/**
 * Erreurs du catalogue (recursos, serviços, horário, encerramentos, reserva online) en pt-PT.
 * Les messages et `fieldErrors` du back sont en français : on s'appuie sur le `code` stable du back
 * (5a-1), puis sur l'opération, le statut HTTP et le nom du champ, jamais sur le texte. Une erreur
 * qui n'est pas une `ApiError` (réseau, jeton absent) donne toujours le message générique.
 */

const RESOURCE_GONE = "Este recurso já não existe.";
const SERVICE_GONE = "Este serviço já não existe.";
const CLOSURE_GONE = "Este encerramento já não existe.";
const STAFF_MEMBER_LINKED = "Este membro da equipa já está ligado a outro recurso.";
const STAFF_LINK_RULE = "Só um profissional pode ser ligado a um membro da equipa.";
const CLOSURE_DATES = "Verifique as datas: o fim deve ser depois do início.";
const CLOSURE_BLOCKED =
  "Não foi possível guardar o encerramento: existem marcações ativas neste período.";
const SCHEDULE_INVALID =
  "Horário inválido: verifique se o fim é depois do início e se os intervalos não se sobrepõem.";
const SETTINGS_INVALID = "Valores inválidos. Verifique os limites indicados em cada campo.";

export type CatalogSubject =
  | "resources"
  | "services"
  | "links"
  | "schedule"
  | "closures"
  | "settings";

const LOAD_MESSAGES: Record<CatalogSubject, string> = {
  resources: "Não foi possível carregar os recursos.",
  services: "Não foi possível carregar os serviços.",
  links: "Não foi possível carregar as associações.",
  schedule: "Não foi possível carregar o horário.",
  closures: "Não foi possível carregar os encerramentos.",
  settings: "Não foi possível carregar as definições de reserva online.",
};

/** Chargement d'une liste ou d'un écran du catalogue. */
export function describeCatalogLoadError(
  error: unknown,
  subject: CatalogSubject,
): string {
  return resolveMessage(error, {
    codes: new Map([
      ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
      ["SERVICE_NOT_FOUND", SERVICE_GONE],
    ]),
    statuses: new Map([[403, "Não tem permissão para ver esta informação."]]),
    generic: LOAD_MESSAGES[subject],
  });
}

/** Chargement du salon par le layout du catalogue. */
export function describeCatalogSalonLoadError(error: unknown): string {
  return resolveMessage(error, {
    statuses: new Map([
      [403, "Não foi possível aceder a este salão."],
      [404, "Não foi possível aceder a este salão."],
    ]),
    generic: "Não foi possível carregar o salão.",
  });
}

const RESOURCE_FIELDS: ReadonlyMap<string, string> = new Map([
  ["name", "Indique o nome do recurso (100 caracteres no máximo)."],
]);

/** Création ou modification d'un recurso (dont le lien avec un membre de l'équipe). */
export function describeResourceSaveError(error: unknown): FormFailure {
  const message = resolveMessage(error, {
    codes: new Map([
      ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
      ["RESOURCE_NAME_ALREADY_USED", "Já existe um recurso com este nome."],
      ["INVALID_RESOURCE_TYPE", "Tipo de recurso inválido."],
      ["RESOURCE_STAFF_LINK_INVALID", STAFF_LINK_RULE],
      ["STAFF_MEMBER_NOT_FOUND", "O membro da equipa escolhido já não existe."],
      ["STAFF_MEMBER_ALREADY_LINKED", STAFF_MEMBER_LINKED],
    ]),
    statuses: new Map([
      [400, `Verifique os dados introduzidos. ${STAFF_LINK_RULE}`],
      [404, "O recurso ou o membro da equipa escolhido já não existe."],
      [409, STAFF_MEMBER_LINKED],
    ]),
    generic: "Não foi possível guardar o recurso. Tente novamente.",
  });
  return { message, fieldErrors: translateFields(error, RESOURCE_FIELDS) };
}

const SERVICE_FIELDS: ReadonlyMap<string, string> = new Map([
  ["name", "Indique o nome do serviço (100 caracteres no máximo)."],
  ["description", "A descrição é demasiado longa (1000 caracteres no máximo)."],
  ["defaultDurationMinutes", "Duração: número inteiro entre 1 e 1440 minutos."],
  ["defaultPriceCents", "Preço inválido (por exemplo, 25 ou 25,50)."],
]);

/** Création ou modification d'un serviço. */
export function describeServiceSaveError(error: unknown): FormFailure {
  const message = resolveMessage(error, {
    codes: new Map([
      ["SERVICE_NOT_FOUND", SERVICE_GONE],
      ["SERVICE_NAME_ALREADY_USED", "Já existe um serviço com este nome."],
    ]),
    statuses: new Map([
      [400, "Verifique os dados introduzidos."],
      [404, SERVICE_GONE],
    ]),
    generic: "Não foi possível guardar o serviço. Tente novamente.",
  });
  return { message, fieldErrors: translateFields(error, SERVICE_FIELDS) };
}

const RESOURCE_IN_USE =
  "Não é possível eliminar este recurso: tem marcações ativas. Cancele-as ou conclua-as primeiro.";
const SERVICE_IN_USE =
  "Não é possível eliminar este serviço: tem marcações ativas. Cancele-as ou conclua-as primeiro.";

/** Suppression d'un recurso ou d'un serviço : conflit = marcações ativas. */
export function describeDeleteError(
  error: unknown,
  subject: "resource" | "service",
): string {
  if (subject === "resource") {
    return resolveMessage(error, {
      codes: new Map([
        ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
        ["RESOURCE_IN_USE", RESOURCE_IN_USE],
      ]),
      statuses: new Map([
        [404, RESOURCE_GONE],
        [409, RESOURCE_IN_USE],
      ]),
      generic: "Não foi possível eliminar o recurso. Tente novamente.",
    });
  }
  return resolveMessage(error, {
    codes: new Map([
      ["SERVICE_NOT_FOUND", SERVICE_GONE],
      ["SERVICE_IN_USE", SERVICE_IN_USE],
    ]),
    statuses: new Map([
      [404, SERVICE_GONE],
      [409, SERVICE_IN_USE],
    ]),
    generic: "Não foi possível eliminar o serviço. Tente novamente.",
  });
}

/** Association serviço ↔ recurso : `action` distingue l'enregistrement du retrait. */
export function describeLinkError(
  error: unknown,
  action: "save" | "remove",
): string {
  return resolveMessage(error, {
    codes: new Map([
      ["SERVICE_NOT_FOUND", SERVICE_GONE],
      ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
      [
        "SERVICE_RESOURCE_LINK_CONFLICT",
        "A associação foi alterada entretanto. Atualize a página e tente novamente.",
      ],
    ]),
    statuses: new Map([
      [400, "Verifique o preço e a duração indicados."],
      [404, "O serviço ou o recurso já não existe."],
    ]),
    generic:
      action === "save"
        ? "Não foi possível associar o recurso. Tente novamente."
        : "Não foi possível retirar a associação. Tente novamente.",
  });
}

/** Enregistrement des horaires hebdomadaires (salon ou recurso). */
export function describeScheduleSaveError(error: unknown): string {
  return resolveMessage(error, {
    codes: new Map([
      ["INVALID_TIME_RANGE", SCHEDULE_INVALID],
      ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
    ]),
    statuses: new Map([
      [400, SCHEDULE_INVALID],
      [404, "O recurso já não existe."],
    ]),
    generic: "Não foi possível guardar o horário. Tente novamente.",
  });
}

/** Création ou modification d'un encerramento (le 409 avec marcações est traité par `parseClosureConflict`). */
export function describeClosureSaveError(error: unknown): string {
  return resolveMessage(error, {
    codes: new Map([
      ["INVALID_TIME_RANGE", CLOSURE_DATES],
      ["CLOSURE_NOT_FOUND", CLOSURE_GONE],
      ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
      ["CLOSURE_BLOCKED_BY_APPOINTMENTS", CLOSURE_BLOCKED],
    ]),
    statuses: new Map([
      [400, CLOSURE_DATES],
      [404, "O recurso ou o encerramento já não existe."],
      [409, CLOSURE_BLOCKED],
    ]),
    generic: "Não foi possível guardar o encerramento. Tente novamente.",
  });
}

export function describeClosureDeleteError(error: unknown): string {
  return resolveMessage(error, {
    codes: new Map([
      ["CLOSURE_NOT_FOUND", CLOSURE_GONE],
      ["RESOURCE_NOT_FOUND", RESOURCE_GONE],
    ]),
    statuses: new Map([[404, CLOSURE_GONE]]),
    generic: "Não foi possível eliminar o encerramento. Tente novamente.",
  });
}

/** Enregistrement des réglages de réservation en ligne. */
export function describeSettingsSaveError(error: unknown): string {
  return resolveMessage(error, {
    codes: new Map([["INVALID_BOOKING_SETTINGS", SETTINGS_INVALID]]),
    statuses: new Map([[400, SETTINGS_INVALID]]),
    generic: "Não foi possível guardar as definições. Tente novamente.",
  });
}
