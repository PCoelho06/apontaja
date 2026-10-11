import { formatMinutes, type Interval } from "@/lib/agenda";
import { zonedLocalToInstant } from "@/lib/zonedTime";

export const DRAG_STEP_MINUTES = 15;
/** En dessous, le mouvement est un clic et non un glisser. */
export const DRAG_THRESHOLD_PX = 5;

/** Arrondi au pas le plus proche (quart d'heure par défaut). */
export function snapMinutes(
  minutes: number,
  step: number = DRAG_STEP_MINUTES,
): number {
  return Math.round(minutes / step) * step;
}

/**
 * Nouveau début (minutes depuis minuit, heure du salon) après un décalage vertical :
 * arrondi au pas, puis borné pour que le RDV reste dans la grille affichée.
 */
export function dragPreviewStart(params: {
  originStart: number;
  duration: number;
  deltaPx: number;
  pxPerMinute: number;
  axis: Interval;
}): number {
  const raw = params.originStart + params.deltaPx / params.pxPerMinute;
  const snapped = snapMinutes(raw);
  const latest =
    Math.floor((params.axis.end - params.duration) / DRAG_STEP_MINUTES) *
    DRAG_STEP_MINUTES;
  return Math.max(params.axis.start, Math.min(snapped, latest));
}

/** Le RDV tient-il entièrement dans un créneau ouvert (horaires moins fermetures) ? */
export function fitsInAvailable(
  start: number,
  duration: number,
  available: readonly Interval[],
): boolean {
  return available.some(
    (interval) => start >= interval.start && start + duration <= interval.end,
  );
}

/** Minutes depuis minuit d'un jour (heure du salon) -> instant ISO. Lève si l'heure n'existe pas. */
export function minutesToInstant(
  date: string,
  minutes: number,
  timeZone: string,
): string {
  return zonedLocalToInstant(`${date}T${formatMinutes(minutes)}`, timeZone);
}
