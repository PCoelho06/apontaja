import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useClosureStore } from "@/stores/closure";

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

const PAYLOAD = {
  startAt: "2099-01-05T08:00:00.000Z",
  endAt: "2099-01-05T12:00:00.000Z",
  reason: null,
};

describe("closure store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("liste les fermetures du salon avec un filtre `from` encodé", async () => {
    vi.mocked(apiGet).mockResolvedValue([]);

    await useClosureStore().fetchClosures("s1", null, "2099-01-01T00:00:00.000Z");

    expect(apiGet).toHaveBeenCalledWith(
      "/api/salons/s1/closures?from=2099-01-01T00%3A00%3A00.000Z",
      "tok",
    );
  });

  it("cible le chemin de la ressource quand elle est fournie", async () => {
    const store = useClosureStore();

    await store.createClosure("s1", "r1", PAYLOAD);
    await store.updateClosure("s1", "r1", "c1", PAYLOAD);
    await store.removeClosure("s1", "r1", "c1");

    expect(apiPost).toHaveBeenCalledWith(
      "/api/salons/s1/resources/r1/closures",
      PAYLOAD,
      "tok",
    );
    expect(apiPut).toHaveBeenCalledWith(
      "/api/salons/s1/resources/r1/closures/c1",
      PAYLOAD,
      "tok",
    );
    expect(apiDelete).toHaveBeenCalledWith(
      "/api/salons/s1/resources/r1/closures/c1",
      "tok",
    );
  });
});
