import { ApiError } from "@apontaja/web-core/api-client";
import {
  TOO_MANY_ATTEMPTS,
  type FormFailure,
} from "@apontaja/web-core/auth-errors";

/**
 * Erreurs du catalogue (recursos, serviços, horário, encerramentos, reserva online) en pt-PT.
 * Les messages et `fieldErrors` du back sont en français : on s'appuie sur l'opération, le statut
 * HTTP et le nom du champ, jamais sur le texte. Une erreur qui n'est pas une `ApiError` (réseau,
 * jeton absent) donne toujours le message générique de l'opération.
 */

const FORBIDDEN = "Não tem permissão para esta ação.";

/** Statut -> message ; 429 et 403 ont une valeur par défaut commune, surchargeable pour 403. */
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

/** Ne garde que les champs connus (Map : une clé arbitraire du back n'atteint jamais un prototype). */
function translateFields(
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

function isBadRequest(error: unknown): boolean {
  return error instanceof ApiError && error.status === 400;
}

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
  return byStatus(
    error,
    new Map([[403, "Não tem permissão para ver esta informação."]]),
    LOAD_MESSAGES[subject],
  );
}

/** Chargement du salon par le layout du catalogue. */
export function describeCatalogSalonLoadError(error: unknown): string {
  return byStatus(
    error,
    new Map([
      [403, "Não foi possível aceder a este salão."],
      [404, "Não foi possível aceder a este salão."],
    ]),
    "Não foi possível carregar o salão.",
  );
}

const RESOURCE_FIELDS: ReadonlyMap<string, string> = new Map([
  ["name", "Indique o nome do recurso (100 caracteres no máximo)."],
]);

/** Création ou modification d'un recurso (dont le lien avec un membre de l'équipe). */
export function describeResourceSaveError(error: unknown): FormFailure {
  const fieldErrors = translateFields(error, RESOURCE_FIELDS);
  const message = isBadRequest(error)
    ? "Verifique os dados introduzidos. Só um profissional pode ser ligado a um membro da equipa."
    : byStatus(
        error,
        new Map([
          [404, "O recurso ou o membro da equipa escolhido já não existe."],
          [409, "Este membro da equipa já está ligado a outro recurso."],
        ]),
        "Não foi possível guardar o recurso. Tente novamente.",
      );
  return { message, fieldErrors };
}

const SERVICE_FIELDS: ReadonlyMap<string, string> = new Map([
  ["name", "Indique o nome do serviço (100 caracteres no máximo)."],
  ["description", "A descrição é demasiado longa (1000 caracteres no máximo)."],
  [
    "defaultDurationMinutes",
    "Duração: número inteiro entre 1 e 1440 minutos.",
  ],
  ["defaultPriceCents", "Preço inválido (por exemplo, 25 ou 25,50)."],
]);

/** Création ou modification d'un serviço. */
export function describeServiceSaveError(error: unknown): FormFailure {
  const fieldErrors = translateFields(error, SERVICE_FIELDS);
  const message = isBadRequest(error)
    ? "Verifique os dados introduzidos."
    : byStatus(
        error,
        new Map([[404, "Este serviço já não existe."]]),
        "Não foi possível guardar o serviço. Tente novamente.",
      );
  return { message, fieldErrors };
}

/** Suppression d'un recurso ou d'un serviço : 409 = marcações ativas. */
export function describeDeleteError(
  error: unknown,
  subject: "resource" | "service",
): string {
  const resource = subject === "resource";
  return byStatus(
    error,
    new Map([
      [
        404,
        resource ? "Este recurso já não existe." : "Este serviço já não existe.",
      ],
      [
        409,
        resource
          ? "Não é possível eliminar este recurso: tem marcações ativas. Cancele-as ou conclua-as primeiro."
          : "Não é possível eliminar este serviço: tem marcações ativas. Cancele-as ou conclua-as primeiro.",
      ],
    ]),
    resource
      ? "Não foi possível eliminar o recurso. Tente novamente."
      : "Não foi possível eliminar o serviço. Tente novamente.",
  );
}

/** Association serviço ↔ recurso : `action` distingue l'enregistrement du retrait. */
export function describeLinkError(
  error: unknown,
  action: "save" | "remove",
): string {
  if (isBadRequest(error)) {
    return "Verifique o preço e a duração indicados.";
  }
  return byStatus(
    error,
    new Map([[404, "O serviço ou o recurso já não existe."]]),
    action === "save"
      ? "Não foi possível associar o recurso. Tente novamente."
      : "Não foi possível retirar a associação. Tente novamente.",
  );
}

/** Enregistrement des horaires hebdomadaires (salon ou recurso). */
export function describeScheduleSaveError(error: unknown): string {
  if (isBadRequest(error)) {
    return "Horário inválido: verifique se o fim é depois do início e se os intervalos não se sobrepõem.";
  }
  return byStatus(
    error,
    new Map([[404, "O recurso já não existe."]]),
    "Não foi possível guardar o horário. Tente novamente.",
  );
}

/** Création ou modification d'un encerramento (le 409 avec marcações est traité par `parseClosureConflict`). */
export function describeClosureSaveError(error: unknown): string {
  if (isBadRequest(error)) {
    return "Verifique as datas: o fim deve ser depois do início.";
  }
  return byStatus(
    error,
    new Map([
      [404, "O recurso ou o encerramento já não existe."],
      [409, "Não foi possível guardar o encerramento: existem marcações ativas neste período."],
    ]),
    "Não foi possível guardar o encerramento. Tente novamente.",
  );
}

export function describeClosureDeleteError(error: unknown): string {
  return byStatus(
    error,
    new Map([[404, "Este encerramento já não existe."]]),
    "Não foi possível eliminar o encerramento. Tente novamente.",
  );
}

/** Enregistrement des réglages de réservation en ligne. */
export function describeSettingsSaveError(error: unknown): string {
  if (isBadRequest(error)) {
    return "Valores inválidos. Verifique os limites indicados em cada campo.";
  }
  return byStatus(
    error,
    new Map(),
    "Não foi possível guardar as definições. Tente novamente.",
  );
}
