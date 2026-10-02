import { ApiError } from "@/lib/apiClient";

const MESSAGES: Record<string, string> = {
  OUTSIDE_SALON_HOURS: "Ce créneau est en dehors des horaires du salon.",
  OUTSIDE_RESOURCE_HOURS: "Ce créneau est en dehors des horaires de la ressource.",
  SALON_CLOSED: "Le salon est fermé sur ce créneau.",
  RESOURCE_CLOSED: "La ressource est fermée sur ce créneau.",
};

/**
 * Problèmes de disponibilité d'un 409 de réservation (`issues[]` du ProblemDetail),
 * en français. Tableau vide pour toute autre erreur.
 */
export function describeIssues(error: unknown): string[] {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return [];
  }
  const issues = error.problem?.issues;
  if (!Array.isArray(issues)) {
    return [];
  }
  return issues
    .filter((issue): issue is string => typeof issue === "string")
    .map((issue) => MESSAGES[issue] ?? issue);
}
