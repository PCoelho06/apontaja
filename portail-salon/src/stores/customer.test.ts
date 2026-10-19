import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  ApiError,
  apiDelete,
  apiGet,
  apiPatch,
  apiPut,
} from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useCustomerStore, type Customer } from "@/stores/customer";

vi.mock("@/lib/apiClient", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/apiClient")>();
  return {
    ...actual,
    apiGet: vi.fn(),
    apiPut: vi.fn(),
    apiPatch: vi.fn(),
    apiDelete: vi.fn(),
  };
});

function customer(id: string, overrides: Partial<Customer> = {}): Customer {
  return {
    customerProfileId: id,
    salonCustomerLinkId: `l-${id}`,
    salonId: "s1",
    firstName: "Alice",
    lastName: "Martin",
    email: "alice@example.com",
    phone: null,
    internalNotes: null,
    marketingConsent: false,
    marketingConsentAt: null,
    marketingConsentRevokedAt: null,
    anonymized: false,
    createdAt: "2026-01-01T00:00:00Z",
    lastVisitAt: null,
    nextVisitAt: null,
    ...overrides,
  };
}

function page(content: Customer[], totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: 1 };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

describe("customer store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("recherche les clients du salon avec page, taille et texte", async () => {
    const alice = customer("c1");
    vi.mocked(apiGet).mockResolvedValue(page([alice], 42));

    const store = useCustomerStore();
    await store.searchCustomers("s1", { q: " ali ", page: 2, size: 10 });

    expect(apiGet).toHaveBeenCalledWith(
      "/api/salons/s1/customers?page=2&size=10&q=ali",
      "tok",
    );
    expect(store.results).toEqual([alice]);
    expect(store.totalElements).toBe(42);
    expect(store.byId["c1"]).toEqual(alice);
  });

  it("n'envoie pas de texte de recherche vide", async () => {
    vi.mocked(apiGet).mockResolvedValue(page([]));

    await useCustomerStore().searchCustomers("s1", { q: "   " });

    expect(apiGet).toHaveBeenCalledWith(
      "/api/salons/s1/customers?page=0&size=20",
      "tok",
    );
  });

  it("ne garde que le résultat de la dernière recherche", async () => {
    const slow = deferred<ReturnType<typeof page>>();
    vi.mocked(apiGet)
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce(page([customer("recent")]));

    const store = useCustomerStore();
    const first = store.searchCustomers("s1", { q: "a" });
    await store.searchCustomers("s1", { q: "ab" });
    slow.resolve(page([customer("old")]));
    await first;

    expect(store.results.map((c) => c.customerProfileId)).toEqual(["recent"]);
  });

  it("ne charge que les clients encore inconnus et ignore les échecs", async () => {
    const store = useCustomerStore();
    store.remember("s1", [customer("known")]);
    vi.mocked(apiGet).mockImplementation((path: string) =>
      path.endsWith("/missing")
        ? Promise.reject(new ApiError(404, "Introuvable"))
        : Promise.resolve(customer("new")),
    );

    await store.ensureCustomers("s1", ["known", "new", "new", "missing"]);

    expect(apiGet).toHaveBeenCalledTimes(2);
    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/customers/new", "tok");
    expect(store.byId["new"]).toBeDefined();
    expect(store.byId["missing"]).toBeUndefined();
  });

  it("vide le cache quand on change de salon", async () => {
    const store = useCustomerStore();
    store.remember("s1", [customer("c1")]);
    vi.mocked(apiGet).mockResolvedValue(customer("c2", { salonId: "s2" }));

    await store.ensureCustomers("s2", ["c2"]);

    expect(store.cacheSalonId).toBe("s2");
    expect(Object.keys(store.byId)).toEqual(["c2"]);
  });

  it("modifie un client par remplacement complet et met le cache à jour", async () => {
    const payload = {
      firstName: "Alicia",
      lastName: "Durand",
      email: null,
      phone: "0612345678",
      internalNotes: null,
    };
    vi.mocked(apiPut).mockResolvedValue(customer("c1", payload));

    const updated = await useCustomerStore().updateCustomer("s1", "c1", payload);

    expect(apiPut).toHaveBeenCalledWith(
      "/api/salons/s1/customers/c1",
      payload,
      "tok",
    );
    expect(updated.firstName).toBe("Alicia");
    expect(useCustomerStore().byId["c1"]?.lastName).toBe("Durand");
  });

  it("enregistre le consentement marketing", async () => {
    vi.mocked(apiPatch).mockResolvedValue(
      customer("c1", { marketingConsent: true }),
    );

    const updated = await useCustomerStore().setMarketingConsent(
      "s1",
      "c1",
      true,
    );

    expect(apiPatch).toHaveBeenCalledWith(
      "/api/salons/s1/customers/c1/marketing-consent",
      { consented: true },
      "tok",
    );
    expect(updated.marketingConsent).toBe(true);
  });

  it("anonymise puis recharge la fiche anonymisée", async () => {
    vi.mocked(apiDelete).mockResolvedValue(undefined);
    vi.mocked(apiGet).mockResolvedValue(
      customer("c1", { anonymized: true, firstName: "Client", email: null }),
    );

    const store = useCustomerStore();
    await store.anonymizeCustomer("s1", "c1");

    expect(apiDelete).toHaveBeenCalledWith("/api/salons/s1/customers/c1", "tok");
    expect(apiGet).toHaveBeenCalledWith("/api/salons/s1/customers/c1", "tok");
    expect(store.byId["c1"]?.anonymized).toBe(true);
  });

  it("charge l'historique paginé d'un client", async () => {
    const history = { content: [], page: 1, size: 5, totalElements: 0, totalPages: 0 };
    vi.mocked(apiGet).mockResolvedValue(history);

    const result = await useCustomerStore().fetchHistory("s1", "c1", 1, 5);

    expect(apiGet).toHaveBeenCalledWith(
      "/api/salons/s1/customers/c1/appointments?page=1&size=5",
      "tok",
    );
    expect(result).toEqual(history);
  });
});
