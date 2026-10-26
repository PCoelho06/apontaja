/** Réglages de réservation en ligne (GET/PUT …/booking-settings). Le back reste l'autorité sur les bornes. */
export interface BookingSettings {
  onlineBookingEnabled: boolean;
  autoConfirm: boolean;
  slotGranularityMinutes: number;
  minNoticeMinutes: number;
  horizonDays: number;
  cancellationDeadlineHours: number;
}

export type NumericField =
  | "minNoticeMinutes"
  | "horizonDays"
  | "cancellationDeadlineHours";

export const GRANULARITY_OPTIONS = [5, 10, 15, 20, 30, 60] as const;

/** Alignées sur BookingSettingsLimits (back) et le CHECK SQL. */
export const LIMITS: Record<NumericField, { min: number; max: number }> = {
  minNoticeMinutes: { min: 0, max: 10_080 },
  horizonDays: { min: 1, max: 365 },
  cancellationDeadlineHours: { min: 0, max: 720 },
};

const FIELD_LABELS: Record<NumericField, string> = {
  minNoticeMinutes: "A antecedência mínima",
  horizonDays: "O limite de reserva",
  cancellationDeadlineHours: "O prazo de cancelamento",
};

/** Valeurs de formulaire : les nombres restent des chaînes tant qu'ils ne sont pas validés. */
export interface SettingsDraft {
  onlineBookingEnabled: boolean;
  autoConfirm: boolean;
  slotGranularityMinutes: string;
  minNoticeMinutes: string;
  horizonDays: string;
  cancellationDeadlineHours: string;
}

export type DraftErrors = Partial<Record<NumericField, string>>;

export function toDraft(settings: BookingSettings): SettingsDraft {
  return {
    onlineBookingEnabled: settings.onlineBookingEnabled,
    autoConfirm: settings.autoConfirm,
    slotGranularityMinutes: String(settings.slotGranularityMinutes),
    minNoticeMinutes: String(settings.minNoticeMinutes),
    horizonDays: String(settings.horizonDays),
    cancellationDeadlineHours: String(settings.cancellationDeadlineHours),
  };
}

function parseField(draft: SettingsDraft, field: NumericField): number | string {
  const raw = draft[field].trim();
  const { min, max } = LIMITS[field];
  const label = FIELD_LABELS[field];
  if (!/^\d+$/.test(raw)) {
    return `${label} deve ser um número inteiro.`;
  }
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < min || value > max) {
    return `${label} deve estar entre ${min} e ${max}.`;
  }
  return value;
}

/** `settings` est null dès qu'une erreur est présente. */
export function parseDraft(draft: SettingsDraft): {
  settings: BookingSettings | null;
  errors: DraftErrors;
} {
  const errors: DraftErrors = {};
  const values = {} as Record<NumericField, number>;
  for (const field of Object.keys(LIMITS) as NumericField[]) {
    const parsed = parseField(draft, field);
    if (typeof parsed === "string") {
      errors[field] = parsed;
    } else {
      values[field] = parsed;
    }
  }
  const granularity = Number(draft.slotGranularityMinutes);
  const granularityValid = (GRANULARITY_OPTIONS as readonly number[]).includes(
    granularity,
  );
  if (Object.keys(errors).length > 0 || !granularityValid) {
    return { settings: null, errors };
  }
  return {
    settings: {
      onlineBookingEnabled: draft.onlineBookingEnabled,
      autoConfirm: draft.autoConfirm,
      slotGranularityMinutes: granularity,
      minNoticeMinutes: values.minNoticeMinutes,
      horizonDays: values.horizonDays,
      cancellationDeadlineHours: values.cancellationDeadlineHours,
    },
    errors,
  };
}

/** État du catalogue utile pour avertir (aucune précondition n'est imposée à l'activation). */
export interface Readiness {
  hasSalonHours: boolean;
  serviceCount: number;
  hasBookableService: boolean;
}

export function readinessWarnings(readiness: Readiness): string[] {
  const warnings: string[] = [];
  if (!readiness.hasSalonHours) {
    warnings.push(
      "O salão não tem horário de abertura definido: não será proposto nenhum horário.",
    );
  }
  if (readiness.serviceCount === 0) {
    warnings.push("Não há nenhum serviço definido: não há nada para reservar.");
  } else if (!readiness.hasBookableService) {
    warnings.push(
      "Nenhum serviço está associado a um recurso: nenhum será proposto online.",
    );
  }
  return warnings;
}

/** Lien public du salon sur le portail client (URL par identifiant ; chemin en portugais, voir `portail-client`). */
export function publicSalonUrl(baseUrl: string, salonId: string): string {
  return `${baseUrl.replace(/\/+$/, "")}/salao/${encodeURIComponent(salonId)}`;
}

export const DEFAULT_CLIENT_PORTAL_URL = "http://localhost:5174";

/** Base du portail client : `VITE_CLIENT_PORTAL_URL`, sinon l'URL de développement. */
export function clientPortalBaseUrl(
  configured: string | undefined = import.meta.env.VITE_CLIENT_PORTAL_URL as
    | string
    | undefined,
): string {
  return configured && configured.trim() !== ""
    ? configured.trim()
    : DEFAULT_CLIENT_PORTAL_URL;
}
