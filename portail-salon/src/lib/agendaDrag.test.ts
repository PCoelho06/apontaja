import { describe, expect, it } from "vitest";

import {
  dragPreviewStart,
  fitsInAvailable,
  minutesToInstant,
  snapMinutes,
} from "@/lib/agendaDrag";

const AXIS = { start: 540, end: 1080 }; // 09:00-18:00
const PX_PER_MINUTE = 56 / 60;

describe("snapMinutes", () => {
  it("arrondit au quart d'heure le plus proche", () => {
    expect(snapMinutes(487)).toBe(480);
    expect(snapMinutes(488)).toBe(495);
    expect(snapMinutes(600)).toBe(600);
  });
});

describe("dragPreviewStart", () => {
  const base = { originStart: 600, duration: 60, pxPerMinute: PX_PER_MINUTE, axis: AXIS };

  it("convertit le décalage en minutes puis arrondit", () => {
    // 28 px ≈ 30 min
    expect(dragPreviewStart({ ...base, deltaPx: 28 })).toBe(630);
    expect(dragPreviewStart({ ...base, deltaPx: -28 })).toBe(570);
    expect(dragPreviewStart({ ...base, deltaPx: 0 })).toBe(600);
  });

  it("reste dans la grille, fin comprise", () => {
    expect(dragPreviewStart({ ...base, deltaPx: -5000 })).toBe(540);
    expect(dragPreviewStart({ ...base, deltaPx: 5000 })).toBe(1020);
    // durée de 50 min : dernier quart d'heure qui laisse la place
    expect(dragPreviewStart({ ...base, duration: 50, deltaPx: 5000 })).toBe(1020);
  });
});

describe("fitsInAvailable", () => {
  const available = [
    { start: 540, end: 720 },
    { start: 780, end: 1080 },
  ];

  it("exige que le RDV tienne entièrement dans un créneau ouvert", () => {
    expect(fitsInAvailable(600, 60, available)).toBe(true);
    expect(fitsInAvailable(660, 60, available)).toBe(true); // finit pile à 12:00
    expect(fitsInAvailable(690, 60, available)).toBe(false); // déborde sur la pause
    expect(fitsInAvailable(700, 120, available)).toBe(false);
    expect(fitsInAvailable(600, 60, [])).toBe(false);
  });
});

describe("minutesToInstant", () => {
  it("convertit une heure du salon en instant ISO", () => {
    expect(minutesToInstant("2099-01-05", 600, "Europe/Paris")).toBe(
      "2099-01-05T09:00:00.000Z",
    );
  });

  it("refuse l'heure qui n'existe pas (saut de printemps)", () => {
    expect(() => minutesToInstant("2026-03-29", 150, "Europe/Paris")).toThrow(
      "n'existe pas",
    );
  });
});
