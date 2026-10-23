import { describe, expect, it } from "vitest";

import {
  cancellationNote,
  findSlot,
  offeringTerms,
  queryValue,
  resourcesForSlot,
  sameInstant,
  summarizeTerms,
} from "@/lib/publicBooking";
import type { PublicOffering, PublicResource } from "@/lib/publicBooking";

const ANA: PublicResource = {
  resourceId: "r1",
  name: "Ana",
  priceCents: 3500,
  durationMinutes: 30,
};
const BEATRIZ: PublicResource = {
  resourceId: "r2",
  name: "Beatriz",
  priceCents: 4000,
  durationMinutes: 45,
};

describe("summarizeTerms", () => {
  it("donne les bornes de prix et de durée", () => {
    expect(summarizeTerms([ANA, BEATRIZ])).toEqual({
      priceMin: 3500,
      priceMax: 4000,
      durationMin: 30,
      durationMax: 45,
    });
  });

  it("renvoie null sans ressource", () => {
    expect(summarizeTerms([])).toBeNull();
  });
});

describe("offeringTerms", () => {
  it("retombe sur les valeurs par défaut sans ressource", () => {
    const offering: PublicOffering = {
      serviceId: "s1",
      name: "Corte",
      description: null,
      defaultDurationMinutes: 40,
      defaultPriceCents: 2500,
      resources: [],
    };

    expect(offeringTerms(offering)).toEqual({
      priceMin: 2500,
      priceMax: 2500,
      durationMin: 40,
      durationMax: 40,
    });
  });
});

describe("resourcesForSlot", () => {
  it("ne garde que les ressources du créneau", () => {
    const slot = { startAt: "2099-06-10T09:00:00Z", resourceIds: ["r2"] };

    expect(resourcesForSlot([ANA, BEATRIZ], slot)).toEqual([BEATRIZ]);
  });

  it("renvoie toutes les ressources si aucune ne correspond", () => {
    const slot = { startAt: "2099-06-10T09:00:00Z", resourceIds: ["inconnue"] };

    expect(resourcesForSlot([ANA, BEATRIZ], slot)).toEqual([ANA, BEATRIZ]);
  });
});

describe("sameInstant / findSlot", () => {
  it("compare des instants et non des chaînes", () => {
    expect(sameInstant("2099-06-10T09:00:00Z", "2099-06-10T10:00:00+01:00")).toBe(true);
    expect(sameInstant("2099-06-10T09:00:00Z", "2099-06-10T09:15:00Z")).toBe(false);
    expect(sameInstant("n'importe quoi", "n'importe quoi")).toBe(false);
  });

  it("retrouve un créneau parmi les jours", () => {
    const days = [
      {
        date: "2099-06-10",
        slots: [
          { startAt: "2099-06-10T09:00:00Z", resourceIds: ["r1"] },
          { startAt: "2099-06-10T09:30:00Z", resourceIds: ["r2"] },
        ],
      },
    ];

    expect(findSlot(days, "2099-06-10T10:30:00+01:00")?.resourceIds).toEqual(["r2"]);
    expect(findSlot(days, "2099-06-10T11:00:00Z")).toBeNull();
  });
});

describe("queryValue", () => {
  it("ne garde que les textes non vides", () => {
    expect(queryValue("abc")).toBe("abc");
    expect(queryValue("")).toBeNull();
    expect(queryValue(["a", "b"])).toBeNull();
    expect(queryValue(undefined)).toBeNull();
  });
});

describe("cancellationNote", () => {
  it("indique le délai d'annulation", () => {
    expect(cancellationNote(24)).toBe(
      "Pode cancelar a marcação até 24 h antes da hora marcada.",
    );
    expect(cancellationNote(0)).toBe(
      "Pode cancelar a marcação até ao início da mesma.",
    );
  });
});
