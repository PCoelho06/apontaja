import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet, apiPut } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useScheduleStore } from "@/stores/schedule";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return { ...actual, apiGet: vi.fn(), apiPut: vi.fn() };
});

describe("schedule store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("lit les horaires du salon et normalise les heures", async () => {
    vi.mocked(apiGet).mockResolvedValue({
      slots: [{ dayOfWeek: "MONDAY", startTime: "09:00:00", endTime: "12:00:00" }],
    });

    const slots = await useScheduleStore().fetchSchedule("s1", null);

    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/schedule", "tok");
    expect(slots).toEqual([
      { dayOfWeek: "MONDAY", startTime: "09:00", endTime: "12:00" },
    ]);
  });

  it("enregistre la semaine d'une ressource en PUT puis relit", async () => {
    vi.mocked(apiGet).mockResolvedValue({ slots: [] });
    const slots = [
      { dayOfWeek: "FRIDAY" as const, startTime: "10:00", endTime: "16:00" },
    ];

    await useScheduleStore().saveSchedule("s1", "r1", slots);

    expect(apiPut).toHaveBeenCalledWith(
      "/api/salons/s1/resources/r1/schedule",
      { slots },
      "tok",
    );
    expect(apiGet).toHaveBeenCalledWith(
      "/api/salons/s1/resources/r1/schedule",
      "tok",
    );
  });
});
