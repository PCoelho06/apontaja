/** Types et règles pures de « mes rendez-vous » (réponses de `/api/me/appointments`). */

import { formatLongDateTime } from "@/lib/format";
import { differsFromBrowserZone } from "@/lib/salonCalendar";

export type AppointmentScope = "upcoming" | "past";

export type BadgeTone = "neutral" | "wine" | "brass" | "danger";

export interface ClientAppointment {
  appointmentId: string;
  salon: { id: string; name: string; city: string; timezone: string };
  service: { id: string; name: string };
  resource: { id: string; name: string };
  startAt: string;
  endAt: string;
  status: string;
  priceCents: number;
  durationMinutes: number;
  cancellable: boolean;
  cancellableUntil: string | null;
  cancelledAt: string | null;
  /** Demande annulée par le système faute de réponse du salon (absent des anciennes fixtures). */
  expired?: boolean;
}

export interface ClientAppointmentPage {
  content: ClientAppointment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Taille de page demandée (le back accepte de 1 à 50, 20 par défaut). */
export const PAGE_SIZE = 10;

export const CANCELLATION_CLOSED_NOTE =
  "O prazo de cancelamento terminou. Contacte o salão.";

export interface StatusInfo {
  label: string;
  tone: BadgeTone;
}

/** Libellé et teinte du badge. Une demande non traitée dont l'heure est passée n'est plus « pendente ». */
export function statusInfo(
  appointment: Pick<ClientAppointment, "status" | "endAt" | "expired">,
  now: Date,
): StatusInfo {
  switch (appointment.status) {
    case "SCHEDULED":
      return new Date(appointment.endAt) <= now
        ? { label: "Pedido sem resposta", tone: "neutral" }
        : { label: "Pedido pendente", tone: "wine" };
    case "CONFIRMED":
      return { label: "Confirmada", tone: "brass" };
    case "COMPLETED":
      return { label: "Concluída", tone: "neutral" };
    case "CANCELLED":
      return appointment.expired
        ? { label: "Pedido expirado", tone: "neutral" }
        : { label: "Cancelada", tone: "danger" };
    case "NO_SHOW":
      return { label: "Não compareceu", tone: "neutral" };
    default:
      return { label: "Estado desconhecido", tone: "neutral" };
  }
}

export type CancellationState = "CANCELLABLE" | "DEADLINE_PASSED" | "NONE";

/**
 * Ce que l'écran propose pour un rendez-vous : annuler, expliquer que le délai est passé (confirmé et
 * encore à venir), ou rien. Le back reste l'autorité (`cancellable`).
 */
export function cancellationState(
  appointment: Pick<ClientAppointment, "cancellable" | "status" | "startAt">,
  now: Date,
): CancellationState {
  if (appointment.cancellable) {
    return "CANCELLABLE";
  }
  if (
    appointment.status === "CONFIRMED" &&
    new Date(appointment.startAt) > now
  ) {
    return "DEADLINE_PASSED";
  }
  return "NONE";
}

/** « Pode cancelar até … » dans le fuseau du salon, ou chaîne vide. */
export function cancellableUntilNote(
  appointment: Pick<ClientAppointment, "cancellableUntil" | "salon">,
): string {
  if (!appointment.cancellableUntil) {
    return "";
  }
  return `Pode cancelar até ${formatLongDateTime(
    appointment.cancellableUntil,
    appointment.salon.timezone,
  )}.`;
}

/** Date et heure du salon ; le fuseau n'est signalé que s'il diffère de celui du navigateur. */
export function whenLabel(
  startAt: string,
  salonTimezone: string,
  browserTimezone: string,
): string {
  const text = formatLongDateTime(startAt, salonTimezone);
  return differsFromBrowserZone(salonTimezone, browserTimezone)
    ? `${text} (hora de ${salonTimezone})`
    : text;
}

/** Ajoute une page à la liste, sans doublon (une annulation ou une création peut décaler les pages). */
export function mergeAppointments(
  existing: ClientAppointment[],
  incoming: ClientAppointment[],
): ClientAppointment[] {
  const known = new Set(existing.map((item) => item.appointmentId));
  return [
    ...existing,
    ...incoming.filter((item) => !known.has(item.appointmentId)),
  ];
}

/** Remplace un rendez-vous par sa version à jour, au même rang. */
export function replaceAppointment(
  list: ClientAppointment[],
  updated: ClientAppointment,
): ClientAppointment[] {
  return list.map((item) =>
    item.appointmentId === updated.appointmentId ? updated : item,
  );
}
