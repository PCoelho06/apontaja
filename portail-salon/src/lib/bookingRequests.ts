import type { AppointmentItem } from "@/stores/agenda";

/** Motif enregistré lorsque le salon refuse une demande (jamais envoyé au client dans l'email). */
export const DECLINE_REASON = "Demande refusée par le salon";

/** Fenêtre de recherche des demandes : au-delà de l'horizon maximal de réservation (365 jours). */
export const REQUEST_WINDOW_DAYS = 400;

/** Demandes à valider : RDV `SCHEDULED` dont le début est à venir, du plus proche au plus lointain. */
export function pendingRequests(
  items: AppointmentItem[],
  now: Date,
): AppointmentItem[] {
  return items
    .filter(
      (item) =>
        item.status === "SCHEDULED" &&
        new Date(item.startAt).getTime() > now.getTime(),
    )
    .sort(
      (a, b) =>
        new Date(a.startAt).getTime() - new Date(b.startAt).getTime() ||
        a.createdAt.localeCompare(b.createdAt),
    );
}

/** Ancienneté d'une demande : « à l'instant », « il y a 5 min », « il y a 3 h », « il y a 2 j ». */
export function ageLabel(createdAt: string, now: Date): string {
  const minutes = Math.floor(
    (now.getTime() - new Date(createdAt).getTime()) / 60_000,
  );
  if (minutes < 1) {
    return "à l'instant";
  }
  if (minutes < 60) {
    return `il y a ${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `il y a ${hours} h`;
  }
  return `il y a ${Math.floor(hours / 24)} j`;
}
