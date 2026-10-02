import { describe, expect, it } from "vitest";

import { availableActions } from "@/lib/appointmentActions";

const FUTURE = "2099-06-01T09:00:00Z";
const PAST = "2020-06-01T09:00:00Z";
const NOW = new Date("2026-10-01T12:00:00Z");

describe("availableActions", () => {
  it("propose confirmer ou annuler un RDV à confirmer, no-show seulement une fois commencé", () => {
    expect(availableActions("SCHEDULED", FUTURE, NOW)).toEqual(["confirm", "cancel"]);
    expect(availableActions("SCHEDULED", PAST, NOW)).toEqual(["confirm", "no-show", "cancel"]);
  });

  it("propose terminer ou annuler un RDV confirmé, no-show seulement une fois commencé", () => {
    expect(availableActions("CONFIRMED", FUTURE, NOW)).toEqual(["complete", "cancel"]);
    expect(availableActions("CONFIRMED", PAST, NOW)).toEqual(["complete", "no-show", "cancel"]);
  });

  it("ne propose rien sur un RDV terminé, annulé ou absent", () => {
    expect(availableActions("COMPLETED", PAST, NOW)).toEqual([]);
    expect(availableActions("CANCELLED", FUTURE, NOW)).toEqual([]);
    expect(availableActions("NO_SHOW", PAST, NOW)).toEqual([]);
  });

  it("considère l'instant exact du début comme commencé", () => {
    expect(availableActions("CONFIRMED", FUTURE, new Date(FUTURE))).toContain("no-show");
  });
});
