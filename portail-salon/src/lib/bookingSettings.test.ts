import { describe, expect, it } from "vitest";

import {
  DEFAULT_CLIENT_PORTAL_URL,
  clientPortalBaseUrl,
  parseDraft,
  publicSalonUrl,
  readinessWarnings,
  toDraft,
  type BookingSettings,
} from "@/lib/bookingSettings";

const settings: BookingSettings = {
  onlineBookingEnabled: true,
  autoConfirm: false,
  slotGranularityMinutes: 15,
  minNoticeMinutes: 120,
  horizonDays: 60,
  cancellationDeadlineHours: 24,
};

describe("bookingSettings", () => {
  it("aller-retour : un brouillon valide redonne les réglages", () => {
    expect(parseDraft(toDraft(settings))).toEqual({ settings, errors: {} });
  });

  it("accepte les bornes incluses", () => {
    const draft = {
      ...toDraft(settings),
      minNoticeMinutes: "0",
      horizonDays: "365",
      cancellationDeadlineHours: "720",
    };
    expect(parseDraft(draft).settings).toMatchObject({
      minNoticeMinutes: 0,
      horizonDays: 365,
      cancellationDeadlineHours: 720,
    });
    expect(
      parseDraft({ ...draft, minNoticeMinutes: "10080" }).settings,
    ).not.toBeNull();
  });

  it("refuse les valeurs hors bornes ou non entières, champ par champ", () => {
    const result = parseDraft({
      ...toDraft(settings),
      minNoticeMinutes: "10081",
      horizonDays: "0",
      cancellationDeadlineHours: "1,5",
    });
    expect(result.settings).toBeNull();
    expect(Object.keys(result.errors).sort()).toEqual([
      "cancellationDeadlineHours",
      "horizonDays",
      "minNoticeMinutes",
    ]);
    expect(result.errors.horizonDays).toContain("entre 1 et 365");
    expect(result.errors.cancellationDeadlineHours).toContain("entier");
  });

  it("refuse une granularité hors liste et un champ vide ou négatif", () => {
    expect(
      parseDraft({ ...toDraft(settings), slotGranularityMinutes: "25" })
        .settings,
    ).toBeNull();
    expect(
      parseDraft({ ...toDraft(settings), horizonDays: "" }).errors.horizonDays,
    ).toBeDefined();
    expect(
      parseDraft({ ...toDraft(settings), minNoticeMinutes: "-5" }).settings,
    ).toBeNull();
  });

  it("avertit selon l'état du catalogue", () => {
    expect(
      readinessWarnings({
        hasSalonHours: true,
        serviceCount: 2,
        hasBookableService: true,
      }),
    ).toEqual([]);
    expect(
      readinessWarnings({
        hasSalonHours: false,
        serviceCount: 0,
        hasBookableService: false,
      }),
    ).toHaveLength(2);
    const unlinked = readinessWarnings({
      hasSalonHours: true,
      serviceCount: 3,
      hasBookableService: false,
    });
    expect(unlinked).toHaveLength(1);
    expect(unlinked[0]).toContain("associée à une ressource");
  });

  it("construit le lien public sans double barre", () => {
    expect(publicSalonUrl("https://client.example.com/", "abc")).toBe(
      "https://client.example.com/salao/abc",
    );
    expect(publicSalonUrl("http://localhost:5174", "abc")).toBe(
      "http://localhost:5174/salao/abc",
    );
  });

  it("retombe sur l'URL de développement sans configuration", () => {
    expect(clientPortalBaseUrl(undefined)).toBe(DEFAULT_CLIENT_PORTAL_URL);
    expect(clientPortalBaseUrl("  ")).toBe(DEFAULT_CLIENT_PORTAL_URL);
    expect(clientPortalBaseUrl(" https://x.fr ")).toBe("https://x.fr");
  });
});
