import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet, apiPut } from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";
import { useBookingSettingsStore } from "@/stores/bookingSettings";

vi.mock("@apontaja/web-core/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@apontaja/web-core/api-client")>();
  return { ...actual, apiGet: vi.fn(), apiPut: vi.fn() };
});

const settings = {
  onlineBookingEnabled: true,
  autoConfirm: false,
  slotGranularityMinutes: 15,
  minNoticeMinutes: 120,
  horizonDays: 60,
  cancellationDeadlineHours: 24,
};

describe("bookingSettings store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("lit les réglages", async () => {
    vi.mocked(apiGet).mockResolvedValue(settings);

    expect(await useBookingSettingsStore().fetchSettings("s1")).toEqual(settings);
    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/booking-settings", "tok");
  });

  it("enregistre en PUT complet", async () => {
    vi.mocked(apiPut).mockResolvedValue(settings);

    await useBookingSettingsStore().saveSettings("s1", settings);

    expect(apiPut).toHaveBeenCalledWith(
      "/api/salons/s1/booking-settings",
      settings,
      "tok",
    );
  });

  function mockCatalog(
    slots: unknown[],
    services: { serviceId: string }[],
    links: Record<string, unknown[]>,
  ) {
    vi.mocked(apiGet).mockImplementation((url: string) => {
      if (url === "/api/salons/s1/schedule") {
        return Promise.resolve({ slots });
      }
      if (url === "/api/salons/s1/services") {
        return Promise.resolve(services);
      }
      const match = /\/services\/([^/]+)\/resources$/.exec(url);
      return Promise.resolve(match ? (links[match[1]] ?? []) : []);
    });
  }

  it("détecte un catalogue prêt", async () => {
    mockCatalog(
      [{ dayOfWeek: "MONDAY", startTime: "09:00:00", endTime: "12:00:00" }],
      [{ serviceId: "a" }, { serviceId: "b" }],
      { b: [{ resourceId: "r1" }] },
    );

    expect(await useBookingSettingsStore().fetchReadiness("s1")).toEqual({
      hasSalonHours: true,
      serviceCount: 2,
      hasBookableService: true,
    });
  });

  it("détecte l'absence d'horaires, de prestations ou d'association", async () => {
    mockCatalog([], [], {});
    expect(await useBookingSettingsStore().fetchReadiness("s1")).toEqual({
      hasSalonHours: false,
      serviceCount: 0,
      hasBookableService: false,
    });

    mockCatalog([], [{ serviceId: "a" }], { a: [] });
    expect(
      (await useBookingSettingsStore().fetchReadiness("s1")).hasBookableService,
    ).toBe(false);
  });
});
