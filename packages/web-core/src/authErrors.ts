import { ApiError } from "./apiClient";

/**
 * Erreurs d'authentification traduites en portugais du Portugal, partagées par les deux portails.
 * Les messages et les `fieldErrors` du back sont en français : on s'appuie sur le statut HTTP et sur
 * le nom du champ, jamais sur le texte.
 */
export const TOO_MANY_ATTEMPTS =
  "Demasiadas tentativas. Tente novamente mais tarde.";

/** Résultat pour un formulaire : message général et messages par champ (clés = noms de champs du back). */
export interface FormFailure {
  message: string;
  fieldErrors: Record<string, string>;
}

function fieldMessage(field: string): string | null {
  switch (field) {
    case "firstName":
      return "Indique o seu nome próprio (máximo de 100 caracteres).";
    case "lastName":
      return "Indique o seu apelido (máximo de 100 caracteres).";
    case "email":
      return "Indique um endereço de email válido.";
    case "password":
    case "newPassword":
      return "A palavra-passe não é válida. Use pelo menos 12 caracteres.";
    default:
      return null;
  }
}

/** Ne garde que les champs connus, avec un texte pt-PT. */
function translateFields(error: ApiError): Record<string, string> {
  const translated: Record<string, string> = {};
  for (const field of Object.keys(error.fieldErrors ?? {})) {
    const message = fieldMessage(field);
    if (message !== null) {
      translated[field] = message;
    }
  }
  return translated;
}

export function describeLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401:
        return "Email ou palavra-passe incorretos.";
      case 429:
        return TOO_MANY_ATTEMPTS;
      case 400:
        return "Verifique o email e a palavra-passe introduzidos.";
      default:
        break;
    }
  }
  return "Não foi possível iniciar sessão. Tente novamente.";
}

export function describeRegisterError(error: unknown): FormFailure {
  const generic = "Não foi possível criar a conta. Tente novamente.";
  if (!(error instanceof ApiError)) {
    return { message: generic, fieldErrors: {} };
  }
  switch (error.status) {
    // Pas de cas 409 : l'inscription ne révèle jamais qu'une adresse a déjà un compte.
    case 429:
      return { message: TOO_MANY_ATTEMPTS, fieldErrors: {} };
    case 400:
      return {
        message: "Verifique os dados introduzidos.",
        fieldErrors: translateFields(error),
      };
    default:
      return { message: generic, fieldErrors: {} };
  }
}

export function describeResetError(error: unknown): FormFailure {
  const generic = "Não foi possível repor a palavra-passe. Tente novamente.";
  if (!(error instanceof ApiError)) {
    return { message: generic, fieldErrors: {} };
  }
  switch (error.status) {
    case 429:
      return { message: TOO_MANY_ATTEMPTS, fieldErrors: {} };
    case 400: {
      const fieldErrors = translateFields(error);
      if (Object.keys(fieldErrors).length > 0) {
        return {
          message: "Verifique a nova palavra-passe.",
          fieldErrors,
        };
      }
      // Sans erreur de champ, le back refuse le jeton (invalide, expiré ou déjà utilisé).
      return {
        message:
          "Esta ligação é inválida ou expirou. Peça uma nova ligação de recuperação.",
        fieldErrors: {},
      };
    }
    default:
      return { message: generic, fieldErrors: {} };
  }
}

export function describeConfirmError(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    return TOO_MANY_ATTEMPTS;
  }
  return "Esta ligação é inválida ou expirou. Peça um novo email de confirmação.";
}
