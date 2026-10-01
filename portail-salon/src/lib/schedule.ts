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
  { code: "MONDAY", label: "Lundi" },
  { code: "TUESDAY", label: "Mardi" },
  { code: "WEDNESDAY", label: "Mercredi" },
  { code: "THURSDAY", label: "Jeudi" },
  { code: "FRIDAY", label: "Vendredi" },
  { code: "SATURDAY", label: "Samedi" },
  { code: "SUNDAY", label: "Dimanche" },
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

/** Message d'erreur en français, ou null si la semaine est envoyable. */
export function validateWeek(week: WeekDays): string | null {
  if (weekToSlots(week).length > MAX_SLOTS) {
    return `Trop de plages (${MAX_SLOTS} au maximum).`;
  }
  for (const day of DAYS) {
    const ranges = [...week[day.code]].sort(byStart);
    for (const [index, range] of ranges.entries()) {
      if (!TIME_PATTERN.test(range.start) || !TIME_PATTERN.test(range.end)) {
        return `${day.label} : renseignez les deux heures de chaque plage.`;
      }
      if (range.end <= range.start) {
        return `${day.label} : la fin doit être après le début (${range.start}–${range.end}).`;
      }
      const next: TimeRange | undefined = ranges[index + 1];
      if (next && TIME_PATTERN.test(next.start) && next.start < range.end) {
        return `${day.label} : les plages ${range.start}–${range.end} et ${next.start}–${next.end} se chevauchent.`;
      }
    }
  }
  return null;
}
