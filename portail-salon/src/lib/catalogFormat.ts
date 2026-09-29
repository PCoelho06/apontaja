const AMOUNT_PATTERN = /^(\d+)(?:[.,](\d{1,2}))?$/;
const MAX_CENTS = 2_147_483_647; // Integer Java

const euroFormat = new Intl.NumberFormat("fr-FR", {
  style: "currency",
  currency: "EUR",
});

/** "12,5" ou "12.50" → 1250. null si invalide (vide, négatif, > 2 décimales). */
export function eurosToCents(input: string): number | null {
  const match = AMOUNT_PATTERN.exec(input.trim());
  if (!match) {
    return null;
  }
  const euros = Number(match[1]);
  const cents = Number((match[2] ?? "").padEnd(2, "0"));
  const total = euros * 100 + cents;
  return Number.isSafeInteger(total) && total <= MAX_CENTS ? total : null;
}

/** 1250 → "12,50 €". */
export function formatCents(cents: number): string {
  return euroFormat.format(cents / 100);
}

/** Valeur pour un champ de saisie : 1250 → "12,50". */
export function centsToInput(cents: number): string {
  return (cents / 100).toFixed(2).replace(".", ",");
}

/** 45 → "45 min", 60 → "1 h", 90 → "1 h 30". */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest} min`;
  }
  if (rest === 0) {
    return `${hours} h`;
  }
  return `${hours} h ${String(rest).padStart(2, "0")}`;
}
