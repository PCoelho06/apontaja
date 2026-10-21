import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiPatch } from "@apontaja/web-core/api-client";
import { useAppointmentStore } from "@/stores/appointment";
import { useAuthStore } from "@/stores/auth";

vi.mock("@apontaja/web-core/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@apontaja/web-core/api-client")>();
  return { ...actual, apiPatch: vi.fn() };
});

describe("appointment store, déplacement", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("envoie le nouveau début en PATCH …/reschedule", async () => {
    await useAppointmentStore().rescheduleAppointment(
      "s1",
      "a1",
      "2099-01-05T13:00:00.000Z",
    );

    expect(apiPatch).toHaveBeenCalledWith(
      "/api/salons/s1/appointments/a1/reschedule",
      { startAt: "2099-01-05T13:00:00.000Z" },
      "tok",
    );
  });
});
