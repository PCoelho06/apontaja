import { describe, expect, it } from "vitest";

import {
  axisRange,
  buildColumns,
  dayBounds,
  formatMinutes,
  intersectIntervals,
  layoutLanes,
  minutesInDay,
  normalizeIntervals,
  shiftDate,
  slotsToIntervals,
  subtractIntervals,
  toMinutes,
  weekdayOf,
  type AgendaAppointment,
  type AgendaClosure,
} from "@/lib/agenda";
import type { ScheduleSlot } from "@/lib/schedule";

describe("minutes et dates", () => {
  it("convertit les heures", () => {
    expect(toMinutes("09:30")).toBe(570);
    expect(toMinutes("09:30:00")).toBe(570);
    expect(formatMinutes(570)).toBe("09:30");
    expect(formatMinutes(1440)).toBe("24:00");
  });

  it("donne le jour de la semaine d'une date de calendrier", () => {
    expect(weekdayOf("2026-10-01")).toBe("THURSDAY");
    expect(weekdayOf("2026-10-04")).toBe("SUNDAY");
    expect(weekdayOf("2099-01-05")).toBe("MONDAY");
  });

  it("décale une date, y compris sur fin de mois, année bissextile et année", () => {
    expect(shiftDate("2026-02-28", 1)).toBe("2026-03-01");
    expect(shiftDate("2024-02-28", 1)).toBe("2024-02-29");
    expect(shiftDate("2026-01-01", -1)).toBe("2025-12-31");
  });
});

describe("dayBounds", () => {
  it("borne un jour ordinaire à minuit du salon", () => {
    expect(dayBounds("2026-10-01", "Europe/Paris")).toEqual({
      from: "2026-09-30T22:00:00.000Z",
      to: "2026-10-01T22:00:00.000Z",
    });
  });

  it("respecte la durée réelle d'un jour de changement d'heure", () => {
    // 25 h : retour à l'heure d'hiver
    expect(dayBounds("2026-10-25", "Europe/Paris")).toEqual({
      from: "2026-10-24T22:00:00.000Z",
      to: "2026-10-25T23:00:00.000Z",
    });
    // 23 h : passage à l'heure d'été
    expect(dayBounds("2026-03-29", "Europe/Paris")).toEqual({
      from: "2026-03-28T23:00:00.000Z",
      to: "2026-03-29T22:00:00.000Z",
    });
  });
});

describe("minutesInDay", () => {
  it("convertit en heure du salon et borne au jour demandé", () => {
    expect(minutesInDay("2026-10-01T08:00:00Z", "Europe/Paris", "2026-10-01")).toBe(600);
    expect(minutesInDay("2026-09-30T20:00:00Z", "Europe/Paris", "2026-10-01")).toBe(0);
    expect(minutesInDay("2026-10-01T22:30:00Z", "Europe/Paris", "2026-10-01")).toBe(1440);
  });
});

describe("intervalles", () => {
  it("fusionne les plages qui se touchent et ignore les autres jours", () => {
    const slots: ScheduleSlot[] = [
      { dayOfWeek: "MONDAY", startTime: "12:00", endTime: "18:00" },
      { dayOfWeek: "MONDAY", startTime: "09:00", endTime: "12:00" },
      { dayOfWeek: "TUESDAY", startTime: "09:00", endTime: "10:00" },
    ];

    expect(slotsToIntervals(slots, "MONDAY")).toEqual([{ start: 540, end: 1080 }]);
  });

  it("normalise, intersecte et soustrait", () => {
    expect(
      normalizeIntervals([
        { start: 60, end: 120 },
        { start: 100, end: 200 },
        { start: 300, end: 300 },
      ]),
    ).toEqual([{ start: 60, end: 200 }]);
    expect(
      intersectIntervals([{ start: 0, end: 100 }], [{ start: 50, end: 150 }]),
    ).toEqual([{ start: 50, end: 100 }]);
    expect(
      subtractIntervals([{ start: 0, end: 100 }], [{ start: 40, end: 60 }]),
    ).toEqual([
      { start: 0, end: 40 },
      { start: 60, end: 100 },
    ]);
    expect(
      subtractIntervals([{ start: 0, end: 100 }], [{ start: 0, end: 100 }]),
    ).toEqual([]);
  });
});

describe("axisRange", () => {
  it("vaut 08:00-20:00 sans horaires ni RDV", () => {
    expect(axisRange([], [])).toEqual({ start: 480, end: 1200 });
  });

  it("arrondit à l'heure et s'étend aux RDV", () => {
    expect(axisRange([{ start: 570, end: 1050 }], [])).toEqual({ start: 540, end: 1080 });
    expect(
      axisRange([{ start: 570, end: 1050 }], [{ start: 420, end: 480 }]),
    ).toEqual({ start: 420, end: 1080 });
  });
});

describe("layoutLanes", () => {
  it("laisse une voie unique aux RDV successifs", () => {
    const placed = layoutLanes([
      { start: 540, end: 600 },
      { start: 600, end: 660 },
    ]);

    expect(placed.map((p) => [p.lane, p.lanes])).toEqual([
      [0, 1],
      [0, 1],
    ]);
  });

  it("place côte à côte des RDV qui se chevauchent", () => {
    const placed = layoutLanes([
      { start: 540, end: 660 },
      { start: 600, end: 720 },
    ]);

    expect(placed.map((p) => [p.lane, p.lanes])).toEqual([
      [0, 2],
      [1, 2],
    ]);
  });

  it("sépare les groupes indépendants", () => {
    const placed = layoutLanes([
      { start: 540, end: 660 },
      { start: 600, end: 720 },
      { start: 800, end: 860 },
    ]);

    expect(placed[2]).toMatchObject({ lane: 0, lanes: 1 });
    expect(placed[0]).toMatchObject({ lanes: 2 });
  });
});

describe("buildColumns", () => {
  const salonSlots: ScheduleSlot[] = [
    { dayOfWeek: "MONDAY", startTime: "09:00", endTime: "18:00" },
  ];
  const resources = [
    { resourceId: "r1", name: "Léa" },
    { resourceId: "r2", name: "Tom" },
    { resourceId: "r3", name: "Four" },
  ];
  const resourceSlots: Record<string, ScheduleSlot[]> = {
    r2: [{ dayOfWeek: "MONDAY", startTime: "10:00", endTime: "12:00" }],
    r3: [{ dayOfWeek: "TUESDAY", startTime: "10:00", endTime: "12:00" }],
  };
  // 2099-01-05 : lundi, heure d'hiver (+01:00) à Paris
  const closures: AgendaClosure[] = [
    {
      resourceId: null,
      startAt: "2099-01-05T11:00:00Z",
      endAt: "2099-01-05T12:00:00Z",
      reason: "Formation",
    },
    {
      resourceId: "r1",
      startAt: "2099-01-05T13:00:00Z",
      endAt: "2099-01-05T14:00:00Z",
      reason: null,
    },
  ];
  const appointments: AgendaAppointment[] = [
    {
      appointmentId: "a1",
      resourceId: "r1",
      startAt: "2099-01-05T08:00:00Z",
      endAt: "2099-01-05T09:00:00Z",
      status: "CONFIRMED",
      customerName: "Alice Martin",
      serviceName: "Coupe",
    },
  ];

  const model = buildColumns({
    date: "2099-01-05",
    timeZone: "Europe/Paris",
    resources,
    appointments,
    salonSlots,
    resourceSlots,
    closures,
  });
  const column = (id: string) => model.columns.find((c) => c.resourceId === id);

  it("applique la règle salon ∩ ressource moins les fermetures", () => {
    // r1 suit le salon (09:00-18:00) moins la fermeture du salon (12-13) et la sienne (14-15)
    expect(column("r1")?.available).toEqual([
      { start: 540, end: 720 },
      { start: 780, end: 840 },
      { start: 900, end: 1080 },
    ]);
    // r2 a ses propres plages (10-12), bornées par le salon
    expect(column("r2")?.available).toEqual([{ start: 600, end: 720 }]);
    // r3 n'a de plages que le mardi : fermée le lundi
    expect(column("r3")?.available).toEqual([]);
  });

  it("répartit les fermetures par colonne", () => {
    expect(column("r1")?.closures).toHaveLength(2);
    expect(column("r2")?.closures).toEqual([
      { start: 720, end: 780, reason: "Formation" },
    ]);
  });

  it("positionne les RDV en heure du salon et borne la grille", () => {
    expect(column("r1")?.appointments).toMatchObject([
      { appointmentId: "a1", start: 540, end: 600, lane: 0, lanes: 1 },
    ]);
    expect(column("r2")?.appointments).toEqual([]);
    expect(model.axis).toEqual({ start: 540, end: 1080 });
  });

  it("borne une fermeture qui commence la veille", () => {
    const clipped = buildColumns({
      date: "2099-01-05",
      timeZone: "Europe/Paris",
      resources: [{ resourceId: "r1", name: "Léa" }],
      appointments: [],
      salonSlots,
      resourceSlots: {},
      closures: [
        {
          resourceId: null,
          startAt: "2099-01-04T20:00:00Z",
          endAt: "2099-01-05T09:00:00Z",
          reason: null,
        },
      ],
    });

    expect(clipped.columns[0]?.closures).toEqual([
      { start: 0, end: 600, reason: null },
    ]);
    expect(clipped.columns[0]?.available).toEqual([{ start: 600, end: 1080 }]);
  });

  it("ferme tout quand le salon n'a aucun horaire", () => {
    const closed = buildColumns({
      date: "2099-01-05",
      timeZone: "Europe/Paris",
      resources: [{ resourceId: "r1", name: "Léa" }],
      appointments: [],
      salonSlots: [],
      resourceSlots: {},
      closures: [],
    });

    expect(closed.columns[0]?.available).toEqual([]);
    expect(closed.axis).toEqual({ start: 480, end: 1200 });
  });
});
