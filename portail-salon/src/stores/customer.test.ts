import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useCustomerStore } from "@/stores/customer";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return { ...actual, apiGet: vi.fn() };
});

describe("customer store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("charge les clients du salon", async () => {
    const customer = { customerProfileId: "c1", firstName: "Alice", lastName: "Martin" };
    vi.mocked(apiGet).mockResolvedValue([customer]);

    const store = useCustomerStore();
    await store.fetchCustomers("s1");

    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/customers", "tok");
    expect(store.customers).toEqual([customer]);
    expect(store.loadedSalonId).toBe("s1");
  });
});
