import { ApiError } from "@/lib/apiClient";

/** Message métier du back (ProblemDetail.detail) si disponible, sinon repli. */
export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}
