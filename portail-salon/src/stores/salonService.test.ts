import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiDelete, apiGet, apiPut } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useSalonServiceStore } from "@/stores/salonService";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return {
    ...actual,
    apiGet: vi.fn(),
    apiPost: vi.fn(),
    apiPut: vi.fn(),
    apiDelete: vi.fn(),
  };
});

describe("salonService store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
    vi.mocked(apiGet).mockResolvedValue([]);
  });

  it("enregistre une association avec ses surcharges puis recharge", async () => {
    const overrides = { overridePriceCents: 3000, overrideDurationMinutes: null };

    await useSalonServiceStore().saveLink("s1", "sv1", "r1", overrides);

    expect(apiPut).toHaveBeenCalledWith(
      "/api/salons/s1/services/sv1/resources/r1",
      overrides,
      "tok",
    );
    expect(apiGet).toHaveBeenCalledWith(
      "/api/salons/s1/services/sv1/resources",
      "tok",
    );
  });

  it("oublie les associations d'une prestation supprimée", async () => {
    const store = useSalonServiceStore();
    store.links["sv1"] = [
      {
        resourceId: "r1",
        overridePriceCents: null,
        overrideDurationMinutes: null,
        effectivePriceCents: 2500,
        effectiveDurationMinutes: 60,
      },
    ];

    await store.removeService("s1", "sv1");

    expect(apiDelete).toHaveBeenCalledWith("/api/salons/s1/services/sv1", "tok");
    expect(store.links["sv1"]).toBeUndefined();
  });
});
