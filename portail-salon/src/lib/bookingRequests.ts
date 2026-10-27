import type { AppointmentItem } from "@/stores/agenda";

/** Motif enregistré lorsque le salon refuse une demande (jamais envoyé au client dans l'email). */
export const DECLINE_REASON = "Pedido recusado pelo salão";

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

/** Ancienneté d'une demande : « agora mesmo », « há 5 min », « há 3 h », « há 2 d ». */
export function ageLabel(createdAt: string, now: Date): string {
  const minutes = Math.floor(
    (now.getTime() - new Date(createdAt).getTime()) / 60_000,
  );
  if (minutes < 1) {
    return "agora mesmo";
  }
  if (minutes < 60) {
    return `há ${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `há ${hours} h`;
  }
  return `há ${Math.floor(hours / 24)} d`;
}



