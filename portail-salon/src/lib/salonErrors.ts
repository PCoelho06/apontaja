import { ApiError } from "@apontaja/web-core/api-client";
import {
  TOO_MANY_ATTEMPTS,
  type FormFailure,
} from "@apontaja/web-core/auth-errors";

/**
 * Erreurs du portail salon (salon, équipe, invitations) en pt-PT. Les messages et `fieldErrors` du back
 * sont en français : on s'appuie sur le statut HTTP et sur le nom du champ, jamais sur le texte.
 */

function salonFieldMessage(field: string): string | null {
  switch (field) {
    case "name":
      return "Indique o nome do salão.";
    case "address":
      return "Indique a morada do salão.";
    case "postalCode":
      return "Indique o código postal.";
    case "city":
      return "Indique a cidade.";
    case "country":
      return "Indique o país.";
    case "timezone":
      return "Escolha um fuso horário válido.";
    case "phone":
      return "O número de telefone não é válido.";
    default:
      return null;
  }
}

export function describeCreateSalonError(error: unknown): FormFailure {
  const generic = "Não foi possível criar o salão. Tente novamente.";
  if (!(error instanceof ApiError)) {
    return { message: generic, fieldErrors: {} };
  }
  switch (error.status) {
    case 429:
      return { message: TOO_MANY_ATTEMPTS, fieldErrors: {} };
    case 400: {
      const fieldErrors: Record<string, string> = {};
      for (const field of Object.keys(error.fieldErrors ?? {})) {
        const message = salonFieldMessage(field);
        if (message !== null) {
          fieldErrors[field] = message;
        }
      }
      return { message: "Verifique os dados introduzidos.", fieldErrors };
    }
    case 409:
      return {
        message: "Já pertence a uma organização e não pode criar outro salão desta forma.",
        fieldErrors: {},
      };
    default:
      return { message: generic, fieldErrors: {} };
  }
}

export function describeSalonsLoadError(): string {
  return "Não foi possível carregar os seus salões.";
}

export function describeSalonLoadError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
    return "Não foi possível aceder a este salão.";
  }
  return "Não foi possível carregar este salão.";
}

export function describeInviteError(error: unknown): string {
  const generic = "Não foi possível enviar o convite. Tente novamente.";
  if (!(error instanceof ApiError)) {
    return generic;
  }
  switch (error.status) {
    case 400:
      return error.fieldErrors && "email" in error.fieldErrors
        ? "Indique um endereço de email válido."
        : "Verifique o email e a função indicados.";
    case 403:
      return "Não tem permissão para convidar para esta função.";
    case 409:
      return "Já existe um convite pendente para este email.";
    case 429:
      return TOO_MANY_ATTEMPTS;
    default:
      return generic;
  }
}

/** Changement de rôle ou retrait d'un membre de l'équipe. */
export function describeMemberActionError(error: unknown): string {
  const generic = "Não foi possível concluir a ação. Tente novamente.";
  if (!(error instanceof ApiError)) {
    return generic;
  }
  switch (error.status) {
    case 403:
      return "Não tem permissão para esta ação.";
    case 404:
      return "Este membro já não faz parte da equipa.";
    case 409:
      return "Esta ação não é possível (por exemplo, não pode retirar nem rebaixar o último proprietário).";
    default:
      return generic;
  }
}

/** Acceptation d'une invitation par un compte connecté. */
export function describeAcceptInvitationError(error: unknown): string {
  const generic = "Não foi possível aceitar o convite. Tente novamente.";
  if (!(error instanceof ApiError)) {
    return generic;
  }
  switch (error.status) {
    case 400:
      return "Este convite é inválido ou expirou.";
    case 403:
      return "O convite foi enviado para um email diferente do da sua conta.";
    case 409:
      return "Já faz parte da equipa deste salão.";
    case 429:
      return TOO_MANY_ATTEMPTS;
    default:
      return generic;
  }
}
