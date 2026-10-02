import type { AppointmentStatus } from "@/lib/appointmentStatus";

export type AppointmentAction = "confirm" | "complete" | "no-show" | "cancel";

/**
 * Actions possibles sur un RDV, alignées sur AppointmentStatusService (back) :
 * confirm SCHEDULED ; complete CONFIRMED ; no-show SCHEDULED/CONFIRMED une fois l'heure de
 * début passée ; cancel SCHEDULED/CONFIRMED. Le back reste l'autorité (409).
 */
export function availableActions(
  status: AppointmentStatus,
  startAt: string,
  now: Date,
): AppointmentAction[] {
  const started = now.getTime() >= new Date(startAt).getTime();
  switch (status) {
    case "SCHEDULED":
      return started
        ? ["confirm", "no-show", "cancel"]
        : ["confirm", "cancel"];
    case "CONFIRMED":
      return started
        ? ["complete", "no-show", "cancel"]
        : ["complete", "cancel"];
    default:
      return [];
  }
}
