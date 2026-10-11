import { describe, expect, it } from "vitest";

import { buildColumns, type AgendaAppointment } from "@/lib/agenda";

function clippedFlag(startAt: string, endAt: string): boolean | undefined {
  const appointment: AgendaAppointment = {
    appointmentId: "a1",
    resourceId: "r1",
    startAt,
    endAt,
    status: "CONFIRMED",
    customerName: "Alice",
    serviceName: "Coupe",
  };
  const { columns } = buildColumns({
    date: "2099-01-05",
    timeZone: "Europe/Paris", // 2099-01 : +01:00
    resources: [{ resourceId: "r1", name: "Léa" }],
    appointments: [appointment],
    salonSlots: [],
    resourceSlots: {},
    closures: [],
  });
  return columns[0]?.appointments[0]?.clipped;
}

describe("PositionedAppointment.clipped", () => {
  it("vaut faux pour un RDV entièrement dans le jour", () => {
    expect(clippedFlag("2099-01-05T09:00:00Z", "2099-01-05T10:00:00Z")).toBe(false);
    // commence à minuit pile (heure du salon)
    expect(clippedFlag("2099-01-04T23:00:00Z", "2099-01-05T01:00:00Z")).toBe(false);
    // finit à minuit pile, le lendemain
    expect(clippedFlag("2099-01-05T21:00:00Z", "2099-01-05T23:00:00Z")).toBe(false);
  });

  it("vaut vrai pour un RDV qui commence la veille ou finit après minuit", () => {
    expect(clippedFlag("2099-01-04T22:00:00Z", "2099-01-04T23:30:00Z")).toBe(true);
    expect(clippedFlag("2099-01-05T21:00:00Z", "2099-01-05T23:30:00Z")).toBe(true);
  });
});
