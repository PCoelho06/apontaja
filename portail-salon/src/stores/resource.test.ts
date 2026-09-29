import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useResourceStore } from "@/stores/resource";

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

describe("resource store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
    vi.mocked(apiGet).mockResolvedValue([]);
  });

  it("crée avec le jeton Bearer puis recharge la liste", async () => {
    await useResourceStore().createResource("s1", {
      name: "Léa",
      type: "EMPLOYEE",
    });

    expect(apiPost).toHaveBeenCalledWith(
      "/api/salons/s1/resources",
      { name: "Léa", type: "EMPLOYEE" },
      "tok",
    );
    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/resources", "tok");
  });

  it("met à jour en PUT et supprime en DELETE", async () => {
    const store = useResourceStore();

    await store.updateResource("s1", "r1", { name: "Four", type: "MACHINE" });
    await store.removeResource("s1", "r1");

    expect(apiPut).toHaveBeenCalledWith(
      "/api/salons/s1/resources/r1",
      { name: "Four", type: "MACHINE" },
      "tok",
    );
    expect(apiDelete).toHaveBeenCalledWith("/api/salons/s1/resources/r1", "tok");
  });

  it("refuse d'appeler l'API sans jeton", async () => {
    useAuthStore().accessToken = null;

    await expect(useResourceStore().fetchResources("s1")).rejects.toThrow(
      "Non authentifié.",
    );
    expect(apiGet).not.toHaveBeenCalled();
  });
});
