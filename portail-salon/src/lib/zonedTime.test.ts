import { describe, expect, it } from "vitest";

import {
  formatInstant,
  instantToZonedLocal,
  isValidTimeZone,
  zonedLocalToInstant,
} from "@/lib/zonedTime";

describe("zonedLocalToInstant", () => {
  it("applique l'offset d'été et d'hiver de Paris", () => {
    expect(zonedLocalToInstant("2026-08-15T10:00", "Europe/Paris")).toBe(
      "2026-08-15T08:00:00.000Z",
    );
    expect(zonedLocalToInstant("2026-01-15T10:00", "Europe/Paris")).toBe(
      "2026-01-15T09:00:00.000Z",
    );
  });

  it("gère un autre fuseau", () => {
    expect(zonedLocalToInstant("2026-07-01T09:00", "America/New_York")).toBe(
      "2026-07-01T13:00:00.000Z",
    );
  });

  it("refuse une heure inexistante (saut de printemps)", () => {
    expect(() =>
      zonedLocalToInstant("2026-03-29T02:30", "Europe/Paris"),
    ).toThrow("n'existe pas");
  });

  it("retient une occurrence cohérente d'une heure ambiguë", () => {
    const iso = zonedLocalToInstant("2026-10-25T02:30", "Europe/Paris");
    expect(instantToZonedLocal(iso, "Europe/Paris")).toBe("2026-10-25T02:30");
  });

  it("refuse un fuseau ou un format invalide", () => {
    expect(() => zonedLocalToInstant("2026-08-15T10:00", "Europe/Nulle")).toThrow(
      "Fuseau horaire",
    );
    expect(() => zonedLocalToInstant("demain", "Europe/Paris")).toThrow(
      "invalides",
    );
  });
});

describe("instantToZonedLocal", () => {
  it("fait l'aller-retour avec zonedLocalToInstant", () => {
    const iso = zonedLocalToInstant("2026-12-31T23:30", "Europe/Paris");
    expect(instantToZonedLocal(iso, "Europe/Paris")).toBe("2026-12-31T23:30");
  });
});

describe("isValidTimeZone", () => {
  it("distingue les identifiants IANA valides", () => {
    expect(isValidTimeZone("Europe/Paris")).toBe(true);
    expect(isValidTimeZone("Europe/Nulle")).toBe(false);
    expect(isValidTimeZone("")).toBe(false);
  });
});

describe("formatInstant", () => {
  it("affiche l'heure du fuseau demandé", () => {
    expect(formatInstant("2026-08-15T08:00:00Z", "Europe/Paris")).toContain(
      "10:00",
    );
  });
});
