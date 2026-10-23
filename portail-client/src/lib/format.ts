/** Formats d'affichage du portail client : portugais du Portugal, heures du salon. */

const LOCALE = "pt-PT";

const MONEY = new Intl.NumberFormat(LOCALE, {
  style: "currency",
  currency: "EUR",
});

export function formatPrice(cents: number): string {
  return MONEY.format(cents / 100);
}

export function formatPriceRange(minCents: number, maxCents: number): string {
  if (minCents === maxCents) {
    return formatPrice(minCents);
  }
  return `de ${formatPrice(minCents)} a ${formatPrice(maxCents)}`;
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${String(rest).padStart(2, "0")}`;
}

export function formatDurationRange(min: number, max: number): string {
  if (min === max) {
    return formatDuration(min);
  }
  return `de ${formatDuration(min)} a ${formatDuration(max)}`;
}

/** Heure murale (« 10:00 ») d'un instant dans le fuseau du salon. */
export function formatTime(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
  }).format(new Date(instant));
}

/** « quarta-feira, 10 de junho de 2099 às 10:00 » dans le fuseau du salon. */
export function formatLongDateTime(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone,
  }).format(new Date(instant));
}

function utcNoon(date: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1, 12));
}

/** « quarta-feira, 10 de junho » pour une date locale `AAAA-MM-JJ`. */
export function formatDayLabel(date: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  }).format(utcNoon(date));
}

/** « 10 de junho » pour une date locale `AAAA-MM-JJ`. */
export function formatDayMonth(date: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  }).format(utcNoon(date));
}
