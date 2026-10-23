import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/stores/auth";
import { useBookingStore } from "@/stores/booking";

const SALON = {
  salonId: "s1",
  name: "Salão Teste",
  address: "Rua do Teste 1",
  postalCode: "1000-001",
  city: "Lisboa",
  country: "Portugal",
  phone: null,
  timezone: "Europe/Lisbon",
  autoConfirm: false,
  minNoticeMinutes: 120,
  horizonDays: 60,
  cancellationDeadlineHours: 24,
};

const OFFERINGS = [
  {
    serviceId: "sv1",
    name: "Corte",
    description: null,
    defaultDurationMinutes: 30,
    defaultPriceCents: 2500,
    resources: [
      { resourceId: "r1", name: "Ana", priceCents: 2500, durationMinutes: 30 },
    ],
  },
];

const SLOTS = {
  serviceId: "sv1",
  timezone: "Europe/Lisbon",
  resources: [],
  days: [
    {
      date: "2099-06-10",
      slots: [{ startAt: "2099-06-10T09:00:00Z", resourceIds: ["r1"] }],
    },
  ],
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("useBookingStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("charge le salon et ses prestations (publics, sans jeton)", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      void init;
      if (path === "/api/public/salons/s1") return json(SALON);
      if (path === "/api/public/salons/s1/services") return json(OFFERINGS);
      return json({}, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useBookingStore();

    await store.loadSalon("s1");

    expect(store.salon?.name).toBe("Salão Teste");
    expect(store.offerings).toHaveLength(1);
    expect(store.salonId).toBe("s1");
    for (const call of fetchMock.mock.calls) {
      const headers = call[1]?.headers as Headers;
      expect(headers.has("Authorization")).toBe(false);
    }
  });

  it("oublie l'ancien salon quand le chargement du nouveau échoue", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void init;
        if (path === "/api/public/salons/s1") return json(SALON);
        if (path === "/api/public/salons/s1/services") return json(OFFERINGS);
        return json({ detail: "Introuvable." }, 404);
      }),
    );
    const store = useBookingStore();
    await store.loadSalon("s1");

    await expect(store.loadSalon("s2")).rejects.toMatchObject({ status: 404 });

    expect(store.salon).toBeNull();
    expect(store.offerings).toEqual([]);
  });

  it("charge les créneaux avec les dates et la ressource, puis les met en cache", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      void path;
      void init;
      return json(SLOTS);
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useBookingStore();

    const first = await store.fetchSlots("s1", "sv1", null, "2099-06-10", "2099-06-16");
    const second = await store.fetchSlots("s1", "sv1", null, "2099-06-10", "2099-06-16");

    expect(first.days).toHaveLength(1);
    expect(second).toEqual(first);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "/api/public/salons/s1/services/sv1/slots?from=2099-06-10&to=2099-06-16",
    );
  });

  it("ajoute la ressource à la requête et la distingue dans le cache", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      void path;
      void init;
      return json(SLOTS);
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useBookingStore();

    await store.fetchSlots("s1", "sv1", null, "2099-06-10", "2099-06-16");
    await store.fetchSlots("s1", "sv1", "r1", "2099-06-10", "2099-06-16");

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      "/api/public/salons/s1/services/sv1/slots?from=2099-06-10&to=2099-06-16&resourceId=r1",
    );
  });

  it("recharge après clearSlots", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      void path;
      void init;
      return json(SLOTS);
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useBookingStore();

    await store.fetchSlots("s1", "sv1", null, "2099-06-10", "2099-06-16");
    store.clearSlots();
    await store.fetchSlots("s1", "sv1", null, "2099-06-10", "2099-06-16");

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("réserve avec le jeton du compte et vide le cache des créneaux", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      void init;
      if (path.startsWith("/api/me/salons/s1/bookings")) {
        return json({ appointmentId: "a1", status: "SCHEDULED" }, 201);
      }
      return json(SLOTS);
    });
    vi.stubGlobal("fetch", fetchMock);
    useAuthStore().accessToken = "tok";
    const store = useBookingStore();
    await store.fetchSlots("s1", "sv1", null, "2099-06-10", "2099-06-16");

    const result = await store.book("s1", {
      serviceId: "sv1",
      resourceId: null,
      startAt: "2099-06-10T09:00:00Z",
      phone: "912345678",
      shareContactConsent: true,
    });

    expect(result.status).toBe("SCHEDULED");
    const call = fetchMock.mock.calls.at(-1);
    expect(call?.[0]).toBe("/api/me/salons/s1/bookings");
    expect(call?.[1]?.method).toBe("POST");
    expect((call?.[1]?.headers as Headers).get("Authorization")).toBe("Bearer tok");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      serviceId: "sv1",
      resourceId: null,
      startAt: "2099-06-10T09:00:00Z",
      phone: "912345678",
      shareContactConsent: true,
    });
    expect(store.slotsCache).toEqual({});
  });

  it("refuse de réserver sans session", async () => {
    vi.stubGlobal("fetch", vi.fn());
    useAuthStore().accessToken = null;

    await expect(
      useBookingStore().book("s1", {
        serviceId: "sv1",
        resourceId: null,
        startAt: "2099-06-10T09:00:00Z",
        shareContactConsent: false,
      }),
    ).rejects.toThrow("Non authentifié.");
    expect(fetch).not.toHaveBeenCalled();
  });
});
