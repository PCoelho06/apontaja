export type AppointmentStatus =
  | "SCHEDULED"
  | "CONFIRMED"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW";

export const STATUS_ORDER: AppointmentStatus[] = [
  "SCHEDULED",
  "CONFIRMED",
  "COMPLETED",
  "NO_SHOW",
  "CANCELLED",
];

export const STATUS_LABELS: Record<AppointmentStatus, string> = {
  SCHEDULED: "À confirmer",
  CONFIRMED: "Confirmé",
  COMPLETED: "Terminé",
  NO_SHOW: "Absent",
  CANCELLED: "Annulé",
};

/** Teinte de l'UiBadge (légende). */
export const STATUS_TONES: Record<
  AppointmentStatus,
  "neutral" | "wine" | "brass" | "danger"
> = {
  SCHEDULED: "brass",
  CONFIRMED: "wine",
  COMPLETED: "neutral",
  NO_SHOW: "danger",
  CANCELLED: "neutral",
};

/** Classes du bloc de RDV dans la grille (tokens du thème uniquement). */
export const STATUS_BLOCK_CLASSES: Record<AppointmentStatus, string> = {
  SCHEDULED: "border-brass bg-brass/15 text-ink",
  CONFIRMED: "border-wine bg-wine/10 text-ink",
  COMPLETED: "border-ink/30 bg-ink/5 text-ink/70",
  NO_SHOW: "border-danger bg-danger/10 text-danger",
  CANCELLED: "border-border bg-white text-ink/40 line-through",
};
