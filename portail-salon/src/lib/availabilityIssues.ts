import { ApiError } from "@apontaja/web-core/api-client";

const UNKNOWN_ISSUE = "Este horário não está disponível.";

const MESSAGES: Record<string, string> = {
  OUTSIDE_SALON_HOURS: "Este horário está fora do horário de funcionamento do salão.",
  OUTSIDE_RESOURCE_HOURS: "Este horário está fora do horário do recurso.",
  SALON_CLOSED: "O salão está encerrado neste horário.",
  RESOURCE_CLOSED: "O recurso está encerrado neste horário.",
};

function messageFor(issue: string): string {
  switch (issue) {
    case "OUTSIDE_SALON_HOURS":
    case "OUTSIDE_RESOURCE_HOURS":
    case "SALON_CLOSED":
    case "RESOURCE_CLOSED":
      return MESSAGES[issue] ?? UNKNOWN_ISSUE;
    default:
      return UNKNOWN_ISSUE;
  }
}

/**
 * Problèmes de disponibilité d'un 409 de réservation (`issues[]` du ProblemDetail), en
 * pt-PT, sans doublon. Un code inconnu donne un message générique (jamais le code brut).
 * Tableau vide pour toute autre erreur.
 */
export function describeIssues(error: unknown): string[] {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return [];
  }
  const issues = error.problem?.issues;
  if (!Array.isArray(issues)) {
    return [];
  }
  const messages = issues
    .filter((issue): issue is string => typeof issue === "string")
    .map(messageFor);
  return [...new Set(messages)];
}
