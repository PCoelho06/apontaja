/** Calendrier du salon : dates locales `AAAA-MM-JJ` et fenêtres de 7 jours. */

/** Date locale (`AAAA-MM-JJ`) d'un instant dans le fuseau donné. */
export function dateInZone(instant: Date, timeZone: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(instant);
  const pick = (type: string) =>
    parts.find((part) => part.type === type)?.value ?? "";
  return `${pick("year")}-${pick("month")}-${pick("day")}`;
}

export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const moved = new Date(
    Date.UTC(year ?? 1970, (month ?? 1) - 1, (day ?? 1) + days),
  );
  return moved.toISOString().slice(0, 10);
}

export interface WeekWindow {
  index: number;
  from: string;
  to: string;
  isFirst: boolean;
  isLast: boolean;
}

/**
 * Fenêtre de 7 jours (`index` 0 = à partir d'aujourd'hui, date locale du salon), bornée par l'horizon
 * de réservation. L'index est ramené dans les bornes.
 */
export function weekWindow(
  today: string,
  horizonDays: number,
  index: number,
): WeekWindow {
  const horizon = Math.max(horizonDays, 0);
  const lastIndex = Math.floor(horizon / 7);
  const clamped = Math.min(Math.max(index, 0), lastIndex);
  const from = addDays(today, clamped * 7);
  const lastAllowed = addDays(today, horizon);
  const weekEnd = addDays(from, 6);
  return {
    index: clamped,
    from,
    to: weekEnd < lastAllowed ? weekEnd : lastAllowed,
    isFirst: clamped === 0,
    isLast: clamped === lastIndex,
  };
}

export function browserZone(): string {
  return new Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function differsFromBrowserZone(
  salonZone: string,
  currentBrowserZone: string,
): boolean {
  return salonZone !== currentBrowserZone;
}
