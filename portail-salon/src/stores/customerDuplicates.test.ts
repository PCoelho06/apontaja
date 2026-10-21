import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as apiClient from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";
import { useCustomerStore, type Customer } from "@/stores/customer";

vi.mock("@apontaja/web-core/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@apontaja/web-core/api-client")>();
  return { ...actual, apiGet: vi.fn(), apiPost: vi.fn() };
});

function customer(id: string): Customer {
  return {
    customerProfileId: id,
    salonCustomerLinkId: `link-${id}`,
    salonId: "salon-1",
    firstName: "Marie",
    lastName: "Durand",
    email: null,
    phone: "0612345678",
    internalNotes: null,
    marketingConsent: false,
    marketingConsentAt: null,
    marketingConsentRevokedAt: null,
    anonymized: false,
    createdAt: "2020-01-01T00:00:00Z",
    lastVisitAt: null,
    nextVisitAt: null,
  };
}

describe("customer store — doublons et fusion", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useAuthStore().accessToken = "tok";
    vi.resetAllMocks();
  });

  it("charge les doublons du salon", async () => {
    vi.mocked(apiClient.apiGet).mockResolvedValue({
      groups: [],
      truncated: false,
    });

    const report = await useCustomerStore().fetchDuplicates("salon-1");

    expect(report.groups).toEqual([]);
    expect(apiClient.apiGet).toHaveBeenCalledWith(
      "/api/salons/salon-1/customers/duplicates",
      "tok",
    );
  });

  it("fusionne, vide le cache des clients et mémorise le survivant", async () => {
    const store = useCustomerStore();
    store.remember("salon-1", [customer("a"), customer("b")]);
    vi.mocked(apiClient.apiPost).mockResolvedValue(customer("a"));

    const survivor = await store.mergeCustomers("salon-1", "a", ["b"]);

    expect(apiClient.apiPost).toHaveBeenCalledWith(
      "/api/salons/salon-1/customers/merge",
      { survivorId: "a", absorbedIds: ["b"] },
      "tok",
    );
    expect(survivor.customerProfileId).toBe("a");
    expect(Object.keys(store.byId)).toEqual(["a"]);
  });
});
