import { describe, expect, it } from "vitest";

import { canReschedule } from "@/lib/appointmentActions";

describe("canReschedule", () => {
  it("n'autorise le déplacement que pour un RDV à confirmer ou confirmé", () => {
    expect(canReschedule("SCHEDULED")).toBe(true);
    expect(canReschedule("CONFIRMED")).toBe(true);
    expect(canReschedule("COMPLETED")).toBe(false);
    expect(canReschedule("CANCELLED")).toBe(false);
    expect(canReschedule("NO_SHOW")).toBe(false);
  });
});
