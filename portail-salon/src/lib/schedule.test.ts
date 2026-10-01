import { describe, expect, it } from "vitest";

import {
  emptyWeek,
  slotsToWeek,
  validateWeek,
  weekToSlots,
  type ScheduleSlot,
} from "@/lib/schedule";

describe("slotsToWeek / weekToSlots", () => {
  it("normalise les secondes, groupe par jour et trie par début", () => {
    const slots: ScheduleSlot[] = [
      { dayOfWeek: "MONDAY", startTime: "14:00:00", endTime: "18:00:00" },
      { dayOfWeek: "MONDAY", startTime: "09:00", endTime: "12:00" },
      { dayOfWeek: "SUNDAY", startTime: "10:00", endTime: "11:00" },
    ];

    const week = slotsToWeek(slots);

    expect(week.MONDAY).toEqual([
      { start: "09:00", end: "12:00" },
      { start: "14:00", end: "18:00" },
    ]);
    expect(weekToSlots(week)).toEqual([
      { dayOfWeek: "MONDAY", startTime: "09:00", endTime: "12:00" },
      { dayOfWeek: "MONDAY", startTime: "14:00", endTime: "18:00" },
      { dayOfWeek: "SUNDAY", startTime: "10:00", endTime: "11:00" },
    ]);
  });
});

describe("validateWeek", () => {
  it("accepte une semaine vide et des plages adjacentes", () => {
    expect(validateWeek(emptyWeek())).toBeNull();
    const week = emptyWeek();
    week.TUESDAY = [
      { start: "09:00", end: "12:00" },
      { start: "12:00", end: "18:00" },
    ];
    expect(validateWeek(week)).toBeNull();
  });

  it("refuse un chevauchement en nommant le jour", () => {
    const week = emptyWeek();
    week.WEDNESDAY = [
      { start: "09:00", end: "13:00" },
      { start: "12:00", end: "18:00" },
    ];
    expect(validateWeek(week)).toContain("Mercredi");
    expect(validateWeek(week)).toContain("chevauchent");
  });

  it("refuse une fin avant le début, une plage vide et un format invalide", () => {
    const week = emptyWeek();
    week.FRIDAY = [{ start: "18:00", end: "09:00" }];
    expect(validateWeek(week)).toContain("Vendredi");

    week.FRIDAY = [{ start: "09:00", end: "" }];
    expect(validateWeek(week)).toContain("deux heures");

    week.FRIDAY = [{ start: "9:00", end: "12:00" }];
    expect(validateWeek(week)).toContain("deux heures");
  });

  it("refuse plus de 100 plages", () => {
    const week = emptyWeek();
    week.MONDAY = Array.from({ length: 101 }, () => ({
      start: "09:00",
      end: "10:00",
    }));
    expect(validateWeek(week)).toContain("100");
  });
});
