import { describe, expect, it } from "vitest";

import { appointmentCountLabel, formatDay } from "@/lib/agenda";

describe("formatDay", () => {
  it("écrit le jour en portugais du Portugal", () => {
    // 10 juin 2026 : un mercredi.
    const label = formatDay("2026-06-10").toLowerCase();

    expect(label).toContain("quarta-feira");
    expect(label).toContain("10");
    expect(label).toContain("junho");
    expect(label).toContain("2026");
  });
});

describe("appointmentCountLabel", () => {
  it("accorde le pluriel", () => {
    expect(appointmentCountLabel(0)).toBe("0 marcações");
    expect(appointmentCountLabel(1)).toBe("1 marcação");
    expect(appointmentCountLabel(3)).toBe("3 marcações");
  });
});
