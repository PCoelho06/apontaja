export type DayCode =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export interface ScheduleSlot {
  dayOfWeek: DayCode;
  startTime: string;
  endTime: string;
}

export interface TimeRange {
  start: string;
  end: string;
}

export type WeekDays = Record<DayCode, TimeRange[]>;

export const DAYS: { code: DayCode; label: string }[] = [
  { code: "MONDAY", label: "Segunda-feira" },
  { code: "TUESDAY", label: "Terça-feira" },
  { code: "WEDNESDAY", label: "Quarta-feira" },
  { code: "THURSDAY", label: "Quinta-feira" },
  { code: "FRIDAY", label: "Sexta-feira" },
  { code: "SATURDAY", label: "Sábado" },
  { code: "SUNDAY", label: "Domingo" },
];

export const MAX_SLOTS = 100;

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

export function emptyWeek(): WeekDays {
  return {
    MONDAY: [],
    TUESDAY: [],
    WEDNESDAY: [],
    THURSDAY: [],
    FRIDAY: [],
    SATURDAY: [],
    SUNDAY: [],
  };
}

/** "09:00:00" -> "09:00" : le back peut renvoyer les secondes. */
export function normalizeSlot(slot: ScheduleSlot): ScheduleSlot {
  return {
    dayOfWeek: slot.dayOfWeek,
    startTime: slot.startTime.slice(0, 5),
    endTime: slot.endTime.slice(0, 5),
  };
}

function byStart(a: TimeRange, b: TimeRange): number {
  return a.start.localeCompare(b.start);
}

export function slotsToWeek(slots: ScheduleSlot[]): WeekDays {
  const week = emptyWeek();
  for (const slot of slots.map(normalizeSlot)) {
    week[slot.dayOfWeek].push({ start: slot.startTime, end: slot.endTime });
  }
  for (const day of DAYS) {
    week[day.code].sort(byStart);
  }
  return week;
}

export function weekToSlots(week: WeekDays): ScheduleSlot[] {
  return DAYS.flatMap((day) =>
    [...week[day.code]].sort(byStart).map((range) => ({
      dayOfWeek: day.code,
      startTime: range.start,
      endTime: range.end,
    })),
  );
}

/** Message d'erreur en pt-PT, ou null si la semaine est envoyable. */
export function validateWeek(week: WeekDays): string | null {
  if (weekToSlots(week).length > MAX_SLOTS) {
    return `Demasiados intervalos (máximo ${MAX_SLOTS}).`;
  }
  for (const day of DAYS) {
    const ranges = [...week[day.code]].sort(byStart);
    for (const [index, range] of ranges.entries()) {
      if (!TIME_PATTERN.test(range.start) || !TIME_PATTERN.test(range.end)) {
        return `${day.label}: indique as duas horas de cada intervalo.`;
      }
      if (range.end <= range.start) {
        return `${day.label}: o fim deve ser depois do início (${range.start}–${range.end}).`;
      }
      const next: TimeRange | undefined = ranges[index + 1];
      if (next && TIME_PATTERN.test(next.start) && next.start < range.end) {
        return `${day.label}: os intervalos ${range.start}–${range.end} e ${next.start}–${next.end} sobrepõem-se.`;
      }
    }
  }
  return null;
}
