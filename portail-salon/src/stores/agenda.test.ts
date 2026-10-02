import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet } from "@/lib/apiClient";
import { useAgendaStore } from "@/stores/agenda";
import { useAuthStore } from "@/stores/auth";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return { ...actual, apiGet: vi.fn() };
});

const FROM = "2026-09-30T22%3A00%3A00.000Z";
const TO = "2026-10-01T22%3A00%3A00.000Z";
const APPOINTMENT = {
  appointmentId: "a1",
  resourceId: "r1",
  startAt: "2026-10-01T08:00:00Z",
  endAt: "2026-10-01T09:00:00Z",
  status: "CONFIRMED",
};
const RESOURCE_CLOSURE = {
  closureId: "c1",
  salonId: "s1",
  resourceId: "r1",
  startAt: "2026-10-01T10:00:00Z",
  endAt: "2026-10-01T11:00:00Z",
  reason: "Formation",
};

describe("agenda store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
    vi.mocked(apiGet).mockImplementation(async (path: string) => {
      if (path.includes("/appointments")) {
        return [APPOINTMENT];
      }
      if (path.includes("/resources/r1/closures")) {
        return [RESOURCE_CLOSURE];
      }
      if (path.includes("/closures")) {
        return [];
      }
      return { slots: [{ dayOfWeek: "MONDAY", startTime: "09:00:00", endTime: "18:00:00" }] };
    });
  });

  it("demande les bornes du jour du salon pour les RDV, horaires et fermetures", async () => {
    await useAgendaStore().loadDay("s1", "2026-10-01", "Europe/Paris", ["r1"]);

    expect(apiGet).toHaveBeenCalledWith(
      `/api/salons/s1/appointments?from=${FROM}&to=${TO}`,
      "tok",
    );
    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/schedule", "tok");
    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/resources/r1/schedule", "tok");
    expect(apiGet).toHaveBeenCalledWith(
      `/api/salons/s1/closures?from=${FROM}&to=${TO}`,
      "tok",
    );
    expect(apiGet).toHaveBeenCalledWith(
      `/api/salons/s1/resources/r1/closures?from=${FROM}&to=${TO}`,
      "tok",
    );
  });

  it("applique le résultat : RDV, horaires normalisés, fermetures regroupées", async () => {
    const store = useAgendaStore();

    await store.loadDay("s1", "2026-10-01", "Europe/Paris", ["r1"]);

    expect(store.appointments).toEqual([APPOINTMENT]);
    expect(store.salonSlots).toEqual([
      { dayOfWeek: "MONDAY", startTime: "09:00", endTime: "18:00" },
    ]);
    expect(store.resourceSlots["r1"]).toHaveLength(1);
    expect(store.closures).toEqual([RESOURCE_CLOSURE]);
  });

  it("refuse un fuseau invalide avant tout appel", async () => {
    await expect(
      useAgendaStore().loadDay("s1", "2026-10-01", "Europe/Nulle", []),
    ).rejects.toThrow("Fuseau horaire");
    expect(apiGet).not.toHaveBeenCalled();
  });
});
