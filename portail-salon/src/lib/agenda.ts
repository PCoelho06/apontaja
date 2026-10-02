import type { AppointmentStatus } from "@/lib/appointmentStatus";
import type { DayCode, ScheduleSlot } from "@/lib/schedule";
import { instantToZonedLocal, zonedLocalToInstant } from "@/lib/zonedTime";

/** Intervalle en minutes depuis minuit, heure du salon (0 à 1440). */
export interface Interval {
  start: number;
  end: number;
}

export const DAY_MINUTES = 1440;

const DEFAULT_AXIS: Interval = { start: 8 * 60, end: 20 * 60 };
const WEEKDAYS: DayCode[] = [
  "SUNDAY",
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
];
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

function pad(value: number, length = 2): string {
  return String(value).padStart(length, "0");
}

function parseDate(date: string): Date {
  const match = DATE_PATTERN.exec(date);
  if (!match) {
    throw new Error(`Date invalide : ${date}`);
  }
  return new Date(
    Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])),
  );
}

// ---------- dates (calendrier, indépendant du fuseau) ----------

export function weekdayOf(date: string): DayCode {
  return WEEKDAYS[parseDate(date).getUTCDay()] as DayCode;
}

export function shiftDate(date: string, days: number): string {
  const shifted = parseDate(date);
  shifted.setUTCDate(shifted.getUTCDate() + days);
  return `${pad(shifted.getUTCFullYear(), 4)}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`;
}

/** Date du jour (YYYY-MM-DD) dans le fuseau du salon. */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  return instantToZonedLocal(now.toISOString(), timeZone).slice(0, 10);
}

export function formatDay(date: string): string {
  return new Intl.DateTimeFormat("fr-FR", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(parseDate(date));
}

/** Bornes du jour (heure du salon) en instants ISO : [from, to). */
export function dayBounds(
  date: string,
  timeZone: string,
): { from: string; to: string } {
  return {
    from: zonedLocalToInstant(`${date}T00:00`, timeZone),
    to: zonedLocalToInstant(`${shiftDate(date, 1)}T00:00`, timeZone),
  };
}

// ---------- minutes ----------

export function toMinutes(time: string): number {
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

export function formatMinutes(minutes: number): string {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

/** Minutes depuis minuit (heure du salon) pour `date`, bornées à [0, 1440]. */
export function minutesInDay(
  iso: string,
  timeZone: string,
  date: string,
): number {
  const local = instantToZonedLocal(iso, timeZone);
  const day = local.slice(0, 10);
  if (day < date) {
    return 0;
  }
  if (day > date) {
    return DAY_MINUTES;
  }
  return toMinutes(local.slice(11, 16));
}

// ---------- intervalles ----------

/** Trie, supprime les intervalles vides et fusionne ceux qui se touchent. */
export function normalizeIntervals(intervals: readonly Interval[]): Interval[] {
  const sorted = intervals
    .filter((i) => i.end > i.start)
    .map((i) => ({ start: i.start, end: i.end }))
    .sort((a, b) => a.start - b.start);
  const merged: Interval[] = [];
  for (const interval of sorted) {
    const last = merged[merged.length - 1];
    if (last && interval.start <= last.end) {
      last.end = Math.max(last.end, interval.end);
    } else {
      merged.push(interval);
    }
  }
  return merged;
}

export function slotsToIntervals(
  slots: readonly ScheduleSlot[],
  day: DayCode,
): Interval[] {
  return normalizeIntervals(
    slots
      .filter((slot) => slot.dayOfWeek === day)
      .map((slot) => ({
        start: toMinutes(slot.startTime),
        end: toMinutes(slot.endTime),
      })),
  );
}

export function intersectIntervals(
  a: readonly Interval[],
  b: readonly Interval[],
): Interval[] {
  const result: Interval[] = [];
  for (const x of normalizeIntervals(a)) {
    for (const y of normalizeIntervals(b)) {
      const start = Math.max(x.start, y.start);
      const end = Math.min(x.end, y.end);
      if (end > start) {
        result.push({ start, end });
      }
    }
  }
  return normalizeIntervals(result);
}

export function subtractIntervals(
  a: readonly Interval[],
  b: readonly Interval[],
): Interval[] {
  let remaining = normalizeIntervals(a);
  for (const cut of normalizeIntervals(b)) {
    remaining = remaining.flatMap((interval) => {
      if (cut.end <= interval.start || cut.start >= interval.end) {
        return [interval];
      }
      const parts: Interval[] = [];
      if (cut.start > interval.start) {
        parts.push({ start: interval.start, end: cut.start });
      }
      if (cut.end < interval.end) {
        parts.push({ start: cut.end, end: interval.end });
      }
      return parts;
    });
  }
  return remaining;
}

/** Plage affichée : horaires du salon et RDV, arrondie à l'heure ; 08:00-20:00 par défaut. */
export function axisRange(
  open: readonly Interval[],
  appointments: readonly Interval[],
): Interval {
  const all = [...open, ...appointments].filter((i) => i.end > i.start);
  if (all.length === 0) {
    return { ...DEFAULT_AXIS };
  }
  const start =
    Math.floor(Math.min(...all.map((i) => i.start)) / 60) * 60;
  const end = Math.ceil(Math.max(...all.map((i) => i.end)) / 60) * 60;
  return { start, end: Math.max(end, start + 60) };
}

/**
 * Répartit des intervalles qui se chevauchent en "voies" côte à côte :
 * `lane` = voie de l'élément, `lanes` = nombre de voies de son groupe.
 */
export function layoutLanes<T extends Interval>(
  items: readonly T[],
): Array<T & { lane: number; lanes: number }> {
  type Placed = T & { lane: number; lanes: number };
  const sorted = [...items].sort(
    (a, b) => a.start - b.start || a.end - b.end,
  );
  const result: Placed[] = [];
  let cluster: Placed[] = [];
  let clusterEnd = -1;
  let laneEnds: number[] = [];

  const flush = () => {
    for (const placed of cluster) {
      placed.lanes = laneEnds.length;
    }
    result.push(...cluster);
    cluster = [];
    laneEnds = [];
  };

  for (const item of sorted) {
    if (cluster.length > 0 && item.start >= clusterEnd) {
      flush();
    }
    let lane = laneEnds.findIndex((end) => end <= item.start);
    if (lane === -1) {
      lane = laneEnds.length;
      laneEnds.push(item.end);
    } else {
      laneEnds[lane] = item.end;
    }
    cluster.push({ ...item, lane, lanes: 1 });
    clusterEnd = Math.max(clusterEnd, item.end);
  }
  flush();
  return result;
}

// ---------- modèle de la grille ----------

export interface AgendaAppointment {
  appointmentId: string;
  resourceId: string;
  startAt: string;
  endAt: string;
  status: AppointmentStatus;
  customerName: string;
  serviceName: string;
}

export interface AgendaClosure {
  resourceId: string | null;
  startAt: string;
  endAt: string;
  reason: string | null;
}

export type PositionedAppointment = AgendaAppointment &
  Interval & { lane: number; lanes: number };

export type ColumnClosure = Interval & { reason: string | null };

export interface AgendaColumn {
  resourceId: string;
  name: string;
  /** Créneaux ouverts à la réservation (horaires, moins les fermetures). */
  available: Interval[];
  closures: ColumnClosure[];
  appointments: PositionedAppointment[];
}

export interface BuildColumnsParams {
  date: string;
  timeZone: string;
  resources: Array<{ resourceId: string; name: string }>;
  appointments: AgendaAppointment[];
  salonSlots: ScheduleSlot[];
  resourceSlots: Record<string, ScheduleSlot[]>;
  closures: AgendaClosure[];
}

/**
 * Disponibilité = salon ouvert ∩ ressource (si elle a ses propres plages, sinon elle suit
 * le salon), moins les fermetures du salon et de la ressource. Même règle que le back.
 */
export function buildColumns(params: BuildColumnsParams): {
  columns: AgendaColumn[];
  axis: Interval;
} {
  const { date, timeZone } = params;
  const day = weekdayOf(date);
  const salonOpen = slotsToIntervals(params.salonSlots, day);

  const closuresOf = (resourceId: string | null): ColumnClosure[] =>
    params.closures
      .filter((closure) => closure.resourceId === resourceId)
      .map((closure) => ({
        start: minutesInDay(closure.startAt, timeZone, date),
        end: minutesInDay(closure.endAt, timeZone, date),
        reason: closure.reason,
      }))
      .filter((closure) => closure.end > closure.start);
  const salonClosures = closuresOf(null);

  const columns = params.resources.map((resource): AgendaColumn => {
    const closures = [...salonClosures, ...closuresOf(resource.resourceId)];
    const ownSlots = params.resourceSlots[resource.resourceId] ?? [];
    const base =
      ownSlots.length > 0
        ? intersectIntervals(salonOpen, slotsToIntervals(ownSlots, day))
        : salonOpen;
    const appointments = layoutLanes(
      params.appointments
        .filter((a) => a.resourceId === resource.resourceId)
        .map((a) => ({
          ...a,
          start: minutesInDay(a.startAt, timeZone, date),
          end: minutesInDay(a.endAt, timeZone, date),
        }))
        .filter((a) => a.end > a.start),
    );
    return {
      resourceId: resource.resourceId,
      name: resource.name,
      available: subtractIntervals(base, closures),
      closures,
      appointments,
    };
  });

  return {
    columns,
    axis: axisRange(
      salonOpen,
      columns.flatMap((column) => column.appointments),
    ),
  };
}
