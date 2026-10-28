import { ApiError } from "@apontaja/web-core/api-client";
import type { FormFailure } from "@apontaja/web-core/auth-errors";

import { resolveMessage, translateFields } from "@/lib/resolveError";

/**
 * Erreurs des écrans Clientes et Duplicados en pt-PT.
 * Les messages et `fieldErrors` du back sont en français : on s'appuie sur le `code` stable du back
 * (5a-1), puis sur l'opération, le statut HTTP et le nom du champ, jamais sur le texte. Une erreur
 * qui n'est pas une `ApiError` (réseau, jeton absent) donne toujours le message générique.
 */

const SALON_UNREACHABLE = "Não foi possível aceder a este salão.";
const CUSTOMER_GONE = "Este cliente já não existe.";
const MANAGERS_ONLY = "Apenas gestores e proprietários";
const CONTACT_REQUIRED =
  "Verifique os dados introduzidos. Indique pelo menos um email ou um telefone.";
const ANONYMIZED_NO_EDIT = "Este cliente foi anonimizado e já não pode ser editado.";
const REFRESHED = "A lista foi atualizada.";

export type CustomerLoadSubject = "list" | "history" | "duplicates" | "salon";

/** Chargement de la liste, de l'historique, des duplicados ou du salon. */
export function describeCustomerLoadError(
  error: unknown,
  subject: CustomerLoadSubject,
): string {
  switch (subject) {
    case "list":
      return resolveMessage(error, {
        statuses: new Map([
          [403, "Não tem permissão para ver os clientes deste salão."],
          [404, SALON_UNREACHABLE],
        ]),
        generic: "Não foi possível carregar os clientes.",
      });
    case "history":
      return resolveMessage(error, {
        codes: new Map([["CUSTOMER_NOT_FOUND", CUSTOMER_GONE]]),
        statuses: new Map([[404, CUSTOMER_GONE]]),
        generic: "Não foi possível carregar o histórico.",
      });
    case "duplicates":
      return resolveMessage(error, {
        statuses: new Map([
          [403, `${MANAGERS_ONLY} podem gerir os duplicados.`],
          [404, SALON_UNREACHABLE],
        ]),
        generic: "Não foi possível carregar os duplicados.",
      });
    default:
      return resolveMessage(error, {
        statuses: new Map([[404, SALON_UNREACHABLE]]),
        generic: "Não foi possível carregar o salão.",
      });
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
  const message = resolveMessage(error, {
    codes: new Map([
      ["CUSTOMER_NOT_FOUND", CUSTOMER_GONE],
      ["CUSTOMER_ANONYMIZED", ANONYMIZED_NO_EDIT],
      ["CUSTOMER_CONTACT_REQUIRED", CONTACT_REQUIRED],
    ]),
    statuses: new Map([
      [400, CONTACT_REQUIRED],
      [404, CUSTOMER_GONE],
      [409, ANONYMIZED_NO_EDIT],
    ]),
    generic: "Não foi possível guardar o cliente. Tente novamente.",
  });
  return { message, fieldErrors: translateFields(error, CUSTOMER_FIELDS) };
}

/** Registo ou retirada do consentimento de marketing. */
export function describeConsentError(error: unknown): string {
  const anonymized =
    "Este cliente foi anonimizado: não é possível alterar o consentimento.";
  return resolveMessage(error, {
    codes: new Map([
      ["CUSTOMER_NOT_FOUND", CUSTOMER_GONE],
      ["CUSTOMER_ANONYMIZED", anonymized],
    ]),
    statuses: new Map([
      [404, CUSTOMER_GONE],
      [409, anonymized],
    ]),
    generic: "Não foi possível atualizar o consentimento.",
  });
}

/** Anonimização : un message précis par code, message combiné si le code est absent ou inconnu. */
export function describeAnonymizeError(error: unknown): string {
  return resolveMessage(error, {
    codes: new Map([
      ["CUSTOMER_NOT_FOUND", CUSTOMER_GONE],
      ["CUSTOMER_ANONYMIZED", "Este cliente já foi anonimizado."],
      // Le back lève CUSTOMER_SHARED aussi pour un profil réclamé par un compte (5a-2).
      [
        "CUSTOMER_SHARED",
        "Não é possível anonimizar este cliente: está associado a uma conta de " +
          "cliente ou a outro salão.",
      ],
      [
        "CUSTOMER_HAS_UPCOMING_APPOINTMENTS",
        "Não é possível anonimizar este cliente: tem marcações futuras. " +
          "Cancele-as ou conclua-as primeiro.",
      ],
    ]),
    statuses: new Map([
      [403, `${MANAGERS_ONLY} podem anonimizar um cliente.`],
      [404, CUSTOMER_GONE],
      [
        409,
        "Não é possível anonimizar este cliente: está associado a uma conta de " +
          "cliente ou a outro salão, ou tem marcações futuras.",
      ],
    ]),
    generic: "Não foi possível anonimizar o cliente.",
  });
}

/** Fusão de perfis. */
export function describeMergeError(error: unknown): string {
  const invalid =
    "Seleção inválida: escolha entre 1 e 10 perfis a fundir, distintos do perfil mantido.";
  return resolveMessage(error, {
    codes: new Map([
      ["INVALID_MERGE_REQUEST", invalid],
      ["CUSTOMER_NOT_FOUND", `Um dos perfis já não existe neste salão. ${REFRESHED}`],
      ["CUSTOMER_ANONYMIZED", `Fusão impossível: um dos perfis foi anonimizado. ${REFRESHED}`],
      [
        "CUSTOMER_CLAIMED",
        "Fusão impossível: um dos perfis está associado a uma conta de cliente " +
          `e só pode ser o perfil mantido. ${REFRESHED}`,
      ],
      [
        "CUSTOMER_SHARED",
        `Fusão impossível: um dos perfis é partilhado com outro salão. ${REFRESHED}`,
      ],
    ]),
    statuses: new Map([
      [400, invalid],
      [403, `${MANAGERS_ONLY} podem fundir clientes.`],
      [404, `Um dos perfis já não existe neste salão. ${REFRESHED}`],
      [
        409,
        "Fusão impossível: um dos perfis foi anonimizado, está associado a uma " +
          `conta de cliente ou é partilhado com outro salão. ${REFRESHED}`,
      ],
    ]),
    generic: "A fusão falhou.",
  });
}

/** 404/409 : la liste des duplicados est périmée, la vue la recharge. */
export function shouldReloadAfterMerge(error: unknown): boolean {
  return (
    error instanceof ApiError && (error.status === 404 || error.status === 409)
  );
}
