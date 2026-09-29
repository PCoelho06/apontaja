import { describe, expect, it } from "vitest";

import {
  centsToInput,
  eurosToCents,
  formatCents,
  formatDuration,
} from "@/lib/catalogFormat";

describe("eurosToCents", () => {
  it.each([
    ["12", 1200],
    ["12,5", 1250],
    ["12.50", 1250],
    ["0", 0],
    ["0,05", 5],
    [" 7 ", 700],
  ])("convertit %j en %d centimes", (input, expected) => {
    expect(eurosToCents(input)).toBe(expected);
  });

  it.each(["", "abc", "-1", "1,234", "1 2", "12,", ",5", "99999999999"])(
    "refuse %j",
    (input) => {
      expect(eurosToCents(input)).toBeNull();
    },
  );
});

describe("formatCents", () => {
  it("formate en euros à la française", () => {
    expect(formatCents(1250).replace(/\s/g, " ")).toBe("12,50 €");
  });
});

describe("centsToInput", () => {
  it("produit une valeur saisissable", () => {
    expect(centsToInput(1250)).toBe("12,50");
    expect(centsToInput(5)).toBe("0,05");
    expect(eurosToCents(centsToInput(1999))).toBe(1999);
  });
});

describe("formatDuration", () => {
  it("formate minutes et heures", () => {
    expect(formatDuration(45)).toBe("45 min");
    expect(formatDuration(60)).toBe("1 h");
    expect(formatDuration(90)).toBe("1 h 30");
    expect(formatDuration(65)).toBe("1 h 05");
  });
});
