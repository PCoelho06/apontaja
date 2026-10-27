import { ApiError } from "@apontaja/web-core/api-client";
import {
  TOO_MANY_ATTEMPTS,
  type FormFailure,
} from "@apontaja/web-core/auth-errors";

/**
 * Erreurs des écrans Clientes et Duplicados en pt-PT.
 * Les messages et `fieldErrors` du back sont en français : on s'appuie sur l'opération, le statut
 * HTTP et le nom du champ, jamais sur le texte. Une erreur qui n'est pas une `ApiError` (réseau,
 * jeton absent) donne toujours le message générique de l'opération.
 */

const FORBIDDEN = "Não tem permissão para esta ação.";
const SALON_UNREACHABLE = "Não foi possível aceder a este salão.";
const CUSTOMER_GONE = "Este cliente já não existe.";
const MANAGERS_ONLY = "Apenas gestores e proprietários";

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

export type CustomerLoadSubject = "list" | "history" | "duplicates" | "salon";

/** Chargement de la liste, de l'historique, des duplicados ou du salon. */
export function describeCustomerLoadError(
  error: unknown,
  subject: CustomerLoadSubject,
): string {
  switch (subject) {
    case "list":
      return byStatus(
        error,
        new Map([
          [403, "Não tem permissão para ver os clientes deste salão."],
          [404, SALON_UNREACHABLE],
        ]),
        "Não foi possível carregar os clientes.",
      );
    case "history":
      return byStatus(
        error,
        new Map([[404, CUSTOMER_GONE]]),
        "Não foi possível carregar o histórico.",
      );
    case "duplicates":
      return byStatus(
        error,
        new Map([
          [403, `${MANAGERS_ONLY} podem gerir os duplicados.`],
          [404, SALON_UNREACHABLE],
        ]),
        "Não foi possível carregar os duplicados.",
      );
    default:
      return byStatus(
        error,
        new Map([[404, SALON_UNREACHABLE]]),
        "Não foi possível carregar o salão.",
      );
  }
}

const CUSTOMER_FIELDS: ReadonlyMap<string, string> = new Map([
  ["firstName", "Indique o nome próprio (100 caracteres no máximo)."],
  ["lastName", "Indique o apelido (100 caracteres no máximo)."],
  ["email", "Email inválido."],
  ["phone", "Telefone inválido."],
  ["internalNotes", "As notas são demasiado longas (2000 caracteres no máximo)."],
]);

/** Criação ou modificação de um cliente. */
export function describeCustomerSaveError(error: unknown): FormFailure {
  const fieldErrors = translateFields(error, CUSTOMER_FIELDS);
  const isBadRequest = error instanceof ApiError && error.status === 400;
  const message = isBadRequest
    ? "Verifique os dados introduzidos. Indique pelo menos um email ou um telefone."
    : byStatus(
        error,
        new Map([
          [404, CUSTOMER_GONE],
          [409, "Este cliente foi anonimizado e já não pode ser editado."],
        ]),
        "Não foi possível guardar o cliente. Tente novamente.",
      );
  return { message, fieldErrors };
}

/** Registo ou retirada do consentimento de marketing. */
export function describeConsentError(error: unknown): string {
  return byStatus(
    error,
    new Map([
      [404, CUSTOMER_GONE],
      [409, "Este cliente foi anonimizado: não é possível alterar o consentimento."],
    ]),
    "Não foi possível atualizar o consentimento.",
  );
}

/** Anonimização (o back não distingue os motivos do 409 : message combiné). */
export function describeAnonymizeError(error: unknown): string {
  return byStatus(
    error,
    new Map([
      [403, `${MANAGERS_ONLY} podem anonimizar um cliente.`],
      [404, CUSTOMER_GONE],
      [
        409,
        "Não é possível anonimizar este cliente: está associado a uma conta de " +
          "cliente ou a outro salão, ou tem marcações futuras.",
      ],
    ]),
    "Não foi possível anonimizar o cliente.",
  );
}

/** Fusão de perfis. */
export function describeMergeError(error: unknown): string {
  return byStatus(
    error,
    new Map([
      [
        400,
        "Seleção inválida: escolha entre 1 e 10 perfis a fundir, distintos do perfil mantido.",
      ],
      [403, `${MANAGERS_ONLY} podem fundir clientes.`],
      [404, "Um dos perfis já não existe neste salão. A lista foi atualizada."],
      [
        409,
        "Fusão impossível: um dos perfis foi anonimizado, está associado a uma " +
          "conta de cliente ou é partilhado com outro salão. A lista foi atualizada.",
      ],
    ]),
    "A fusão falhou.",
  );
}

/** 404/409 : la liste des duplicados est périmée, la vue la recharge. */
export function shouldReloadAfterMerge(error: unknown): boolean {
  return (
    error instanceof ApiError && (error.status === 404 || error.status === 409)
  );
}
