import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet, apiPost } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useCustomerStore } from "@/stores/customer";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return { ...actual, apiGet: vi.fn(), apiPost: vi.fn() };
});

const PAYLOAD = {
  firstName: "Alice",
  lastName: "Martin",
  email: "alice@example.com",
  phone: null,
  internalNotes: null,
};

function customer(id: string, createdAt: string) {
  return { customerProfileId: id, firstName: "Alice", lastName: "Martin", createdAt };
}

describe("customer store, création", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("renvoie le profil identifié par la réponse du back", async () => {
    vi.mocked(apiPost).mockResolvedValue({ customerProfileId: "c2" });
    vi.mocked(apiGet).mockResolvedValue([
      customer("c1", "2026-01-01T00:00:00Z"),
      customer("c2", "2026-02-01T00:00:00Z"),
    ]);

    const created = await useCustomerStore().createCustomer("s1", PAYLOAD);

    expect(apiPost).toHaveBeenCalledWith("/api/salons/s1/customers", PAYLOAD, "tok");
    expect(created.customerProfileId).toBe("c2");
  });

  it("retrouve le dernier profil du même nom si la réponse ne contient pas l'identifiant", async () => {
    vi.mocked(apiPost).mockResolvedValue(undefined);
    vi.mocked(apiGet).mockResolvedValue([
      customer("c1", "2026-01-01T00:00:00Z"),
      customer("c2", "2026-02-01T00:00:00Z"),
    ]);

    const created = await useCustomerStore().createCustomer("s1", PAYLOAD);

    expect(created.customerProfileId).toBe("c2");
  });

  it("échoue si le client est introuvable après création", async () => {
    vi.mocked(apiPost).mockResolvedValue(undefined);
    vi.mocked(apiGet).mockResolvedValue([]);

    await expect(useCustomerStore().createCustomer("s1", PAYLOAD)).rejects.toThrow(
      "introuvable",
    );
  });
});
