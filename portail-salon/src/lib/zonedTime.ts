/**
 * Conversions heure locale d'un fuseau IANA <-> instants ISO, sans bibliothèque.
 * Les fermetures sont saisies en heure du salon (Salon.timezone) et envoyées
 * au back en instants ISO 8601.
 */

interface WallTime {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
}

const LOCAL_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

export function isValidTimeZone(timeZone: string): boolean {
  try {
    new Intl.DateTimeFormat("en", { timeZone });
    return timeZone.trim() !== "";
  } catch {
    return false;
  }
}

function wallTime(date: Date, timeZone: string): WallTime {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).formatToParts(date);
  const read = (type: Intl.DateTimeFormatPartTypes): number =>
    Number(parts.find((p) => p.type === type)?.value);
  return {
    year: read("year"),
    month: read("month"),
    day: read("day"),
    hour: read("hour"),
    minute: read("minute"),
  };
}

function asUtcMillis(w: WallTime): number {
  return Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute);
}

function pad(value: number, length = 2): string {
  return String(value).padStart(length, "0");
}

/**
 * "2026-08-15T10:00" (heure murale dans timeZone) -> instant ISO UTC.
 * Heure ambiguë (retour d'automne) : la seconde occurrence est retenue.
 * Heure inexistante (saut de printemps) : erreur explicite.
 */
export function zonedLocalToInstant(local: string, timeZone: string): string {
  if (!isValidTimeZone(timeZone)) {
    throw new Error(`Fuseau horaire du salon invalide : « ${timeZone} ».`);
  }
  const match = LOCAL_PATTERN.exec(local);
  if (!match) {
    throw new Error("Date et heure invalides.");
  }
  const wanted = Date.UTC(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4]),
    Number(match[5]),
  );
  let guess = wanted;
  for (let i = 0; i < 2; i++) {
    guess += wanted - asUtcMillis(wallTime(new Date(guess), timeZone));
  }
  if (asUtcMillis(wallTime(new Date(guess), timeZone)) !== wanted) {
    throw new Error(
      "Cette heure n'existe pas dans le fuseau du salon (changement d'heure).",
    );
  }
  return new Date(guess).toISOString();
}

/** Instant ISO -> "YYYY-MM-DDTHH:mm" en heure murale (valeur d'un input datetime-local). */
export function instantToZonedLocal(iso: string, timeZone: string): string {
  const w = wallTime(new Date(iso), timeZone);
  return `${pad(w.year, 4)}-${pad(w.month)}-${pad(w.day)}T${pad(w.hour)}:${pad(w.minute)}`;
}

export function formatInstant(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat("fr-FR", {
    timeZone,
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));
}
