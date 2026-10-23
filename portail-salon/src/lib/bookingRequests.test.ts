import { describe, expect, it } from "vitest";

import { ageLabel, pendingRequests } from "@/lib/bookingRequests";
import type { AppointmentItem } from "@/stores/agenda";

const NOW = new Date("2099-06-01T10:00:00Z");

function item(
  id: string,
  status: AppointmentItem["status"],
  startAt: string,
  createdAt = "2099-05-30T10:00:00Z",
): AppointmentItem {
  return {
    appointmentId: id,
    salonId: "s",
    customerProfileId: "c",
    serviceId: "svc",
    resourceId: "r",
    startAt,
    endAt: startAt,
    status,
    priceAtBookingCents: 1000,
    durationAtBookingMinutes: 30,
    createdAt,
  };
}

describe("bookingRequests", () => {
  it("ne garde que les demandes à venir, triées par début puis ancienneté", () => {
    const result = pendingRequests(
      [
        item("late", "SCHEDULED", "2099-06-10T10:00:00Z"),
        item("confirmed", "CONFIRMED", "2099-06-05T10:00:00Z"),
        item("past", "SCHEDULED", "2099-05-31T10:00:00Z"),
        item("started", "SCHEDULED", "2099-06-01T10:00:00Z"),
        item("early-new", "SCHEDULED", "2099-06-02T10:00:00Z", "2099-05-31T10:00:00Z"),
        item("early-old", "SCHEDULED", "2099-06-02T10:00:00Z", "2099-05-29T10:00:00Z"),
      ],
      NOW,
    );

    expect(result.map((r) => r.appointmentId)).toEqual([
      "early-old",
      "early-new",
      "late",
    ]);
  });

  it("libelle l'ancienneté", () => {
    expect(ageLabel("2099-06-01T09:59:30Z", NOW)).toBe("à l'instant");
    expect(ageLabel("2099-06-01T09:55:00Z", NOW)).toBe("il y a 5 min");
    expect(ageLabel("2099-06-01T07:00:00Z", NOW)).toBe("il y a 3 h");
    expect(ageLabel("2099-05-30T10:00:00Z", NOW)).toBe("il y a 2 j");
  });
});
