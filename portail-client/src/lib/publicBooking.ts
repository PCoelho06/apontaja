/** Types et règles pures de la réservation en ligne (réponses de `/api/public/**` et `/api/me/**`). */

export interface PublicSalon {
  salonId: string;
  name: string;
  address: string | null;
  postalCode: string | null;
  city: string;
  country: string | null;
  phone: string | null;
  timezone: string;
  autoConfirm: boolean;
  minNoticeMinutes: number;
  horizonDays: number;
  cancellationDeadlineHours: number;
}

export interface PublicResource {
  resourceId: string;
  name: string;
  priceCents: number;
  durationMinutes: number;
}

export interface PublicOffering {
  serviceId: string;
  name: string;
  description: string | null;
  defaultDurationMinutes: number;
  defaultPriceCents: number;
  resources: PublicResource[];
}

export interface PublicSlot {
  startAt: string;
  resourceIds: string[];
}

export interface PublicDay {
  date: string;
  slots: PublicSlot[];
}

export interface PublicSlots {
  serviceId: string;
  timezone: string;
  resources: PublicResource[];
  days: PublicDay[];
}

export interface BookingRequest {
  serviceId: string;
  resourceId: string | null;
  startAt: string;
  phone?: string;
  shareContactConsent: boolean;
}

export interface OnlineBookingResult {
  appointmentId: string;
  salonId: string;
  serviceId: string;
  serviceName: string;
  resourceId: string;
  resourceName: string;
  startAt: string;
  endAt: string;
  status: string;
  priceCents: number;
  durationMinutes: number;
  createdAt: string;
}

export interface Terms {
  priceMin: number;
  priceMax: number;
  durationMin: number;
  durationMax: number;
}

/** Fourchette de prix et de durée d'un ensemble de ressources ; `null` si l'ensemble est vide. */
export function summarizeTerms(
  resources: readonly PublicResource[],
): Terms | null {
  if (resources.length === 0) {
    return null;
  }
  const prices = resources.map((resource) => resource.priceCents);
  const durations = resources.map((resource) => resource.durationMinutes);
  return {
    priceMin: Math.min(...prices),
    priceMax: Math.max(...prices),
    durationMin: Math.min(...durations),
    durationMax: Math.max(...durations),
  };
}

/** Conditions d'une prestation : ressources associées, sinon valeurs par défaut. */
export function offeringTerms(offering: PublicOffering): Terms {
  return (
    summarizeTerms(offering.resources) ?? {
      priceMin: offering.defaultPriceCents,
      priceMax: offering.defaultPriceCents,
      durationMin: offering.defaultDurationMinutes,
      durationMax: offering.defaultDurationMinutes,
    }
  );
}

/** Ressources pouvant prendre le créneau ; toutes les ressources si l'intersection est vide. */
export function resourcesForSlot(
  resources: readonly PublicResource[],
  slot: PublicSlot,
): PublicResource[] {
  const matching = resources.filter((resource) =>
    slot.resourceIds.includes(resource.resourceId),
  );
  return matching.length > 0 ? matching : [...resources];
}

/** Deux écritures ISO désignent-elles le même instant ? */
export function sameInstant(a: string, b: string): boolean {
  const first = Date.parse(a);
  const second = Date.parse(b);
  return !Number.isNaN(first) && first === second;
}

export function findSlot(
  days: readonly PublicDay[],
  startAt: string,
): PublicSlot | null {
  for (const day of days) {
    const slot = day.slots.find((candidate) =>
      sameInstant(candidate.startAt, startAt),
    );
    if (slot) {
      return slot;
    }
  }
  return null;
}

/** Valeur de query texte non vide, sinon `null` (les tableaux sont ignorés). */
export function queryValue(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}

/** Phrase sur le délai d'annulation du salon (pt-PT). */
export function cancellationNote(hours: number): string {
  if (hours <= 0) {
    return "Pode cancelar a marcação até ao início da mesma.";
  }
  return `Pode cancelar a marcação até ${hours} h antes da hora marcada.`;
}
