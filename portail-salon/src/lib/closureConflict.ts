import { ApiError } from "@/lib/apiClient";

export interface BlockingAppointment {
  appointmentId: string;
  resourceId: string;
  startAt: string;
  endAt: string;
}

export interface ClosureConflict {
  /** Nombre total de RDV actifs recouverts (la liste est tronquée à 20 par le back). */
  count: number;
  appointments: BlockingAppointment[];
}

/** Lit le 409 "fermeture recouvrant des RDV actifs" (tranche 8b), sinon null. */
export function parseClosureConflict(error: unknown): ClosureConflict | null {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return null;
  }
  const problem = error.problem;
  if (
    !problem ||
    typeof problem.count !== "number" ||
    !Array.isArray(problem.appointments)
  ) {
    return null;
  }
  return {
    count: problem.count,
    appointments: problem.appointments as BlockingAppointment[],
  };
}
