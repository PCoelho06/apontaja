import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiPatch, apiPost } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useAppointmentStore } from "@/stores/appointment";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return { ...actual, apiPost: vi.fn(), apiPatch: vi.fn() };
});

describe("appointment store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("crée un RDV avec le corps attendu par le back", async () => {
    const payload = {
      customerProfileId: "c1",
      serviceId: "sv1",
      resourceId: "r1",
      startAt: "2099-01-05T09:00:00.000Z",
    };

    await useAppointmentStore().createAppointment("s1", payload);

    expect(apiPost).toHaveBeenCalledWith("/api/salons/s1/appointments", payload, "tok");
  });

  it("appelle les transitions sans corps", async () => {
    const store = useAppointmentStore();

    await store.confirmAppointment("s1", "a1");
    await store.completeAppointment("s1", "a1");
    await store.markNoShow("s1", "a1");

    expect(apiPatch).toHaveBeenCalledWith("/api/salons/s1/appointments/a1/confirm", undefined, "tok");
    expect(apiPatch).toHaveBeenCalledWith("/api/salons/s1/appointments/a1/complete", undefined, "tok");
    expect(apiPatch).toHaveBeenCalledWith("/api/salons/s1/appointments/a1/no-show", undefined, "tok");
  });

  it("annule avec un motif, ou avec un corps vide sans motif", async () => {
    const store = useAppointmentStore();

    await store.cancelAppointment("s1", "a1", "Client malade");
    await store.cancelAppointment("s1", "a1", null);

    expect(apiPatch).toHaveBeenNthCalledWith(
      1,
      "/api/salons/s1/appointments/a1/cancel",
      { reason: "Client malade" },
      "tok",
    );
    expect(apiPatch).toHaveBeenNthCalledWith(
      2,
      "/api/salons/s1/appointments/a1/cancel",
      {},
      "tok",
    );
  });
});
