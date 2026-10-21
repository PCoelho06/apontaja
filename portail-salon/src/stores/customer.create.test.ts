import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet, apiPost } from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";
import { useCustomerStore } from "@/stores/customer";

vi.mock("@apontaja/web-core/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@apontaja/web-core/api-client")>();
  return { ...actual, apiGet: vi.fn(), apiPost: vi.fn() };
});

const PAYLOAD = {
  firstName: "Alice",
  lastName: "Martin",
  email: "alice@example.com",
  phone: null,
  internalNotes: null,
};

describe("customer store, création", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("renvoie le profil créé par le back et le met en cache sans recharger de liste", async () => {
    const created = { customerProfileId: "c2", ...PAYLOAD };
    vi.mocked(apiPost).mockResolvedValue(created);

    const store = useCustomerStore();
    const result = await store.createCustomer("s1", PAYLOAD);

    expect(apiPost).toHaveBeenCalledWith(
      "/api/salons/s1/customers",
      PAYLOAD,
      "tok",
    );
    expect(apiGet).not.toHaveBeenCalled();
    expect(result).toEqual(created);
    expect(store.byId["c2"]).toEqual(created);
  });
});
