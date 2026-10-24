import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ClientAppointment } from "@/lib/myAppointments";
import { useAppointmentsStore } from "@/stores/appointments";
import { useAuthStore } from "@/stores/auth";

function appointment(id: string, status = "CONFIRMED"): ClientAppointment {
  return {
    appointmentId: id,
    salon: { id: "s1", name: "Salão Teste", city: "Lisboa", timezone: "Europe/Lisbon" },
    service: { id: "sv1", name: "Corte" },
    resource: { id: "r1", name: "Ana" },
    startAt: "2099-06-10T09:00:00Z",
    endAt: "2099-06-10T09:30:00Z",
    status,
    priceCents: 2500,
    durationMinutes: 30,
    cancellable: status === "CONFIRMED",
    cancellableUntil: "2099-06-09T09:00:00Z",
    cancelledAt: null,
  };
}

function page(content: ClientAppointment[], pageIndex: number, totalPages: number) {
  return {
    content,
    page: pageIndex,
    size: 10,
    totalElements: 3,
    totalPages,
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("store appointments", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useAuthStore().accessToken = "tok";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("charge la première page avec le jeton", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      void path;
      void init;
      return json(page([appointment("a")], 0, 2));
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useAppointmentsStore();

    await store.load("upcoming");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "/api/me/appointments?scope=upcoming&page=0&size=10",
    );
    expect((fetchMock.mock.calls[0]?.[1]?.headers as Headers).get("Authorization")).toBe(
      "Bearer tok",
    );
    expect(store.items).toHaveLength(1);
    expect(store.hasMore).toBe(true);
  });

  it("ajoute la page suivante sans doublon", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string) =>
        path.includes("page=0")
          ? json(page([appointment("a"), appointment("b")], 0, 2))
          : json(page([appointment("b"), appointment("c")], 1, 2)),
      ),
    );
    const store = useAppointmentsStore();
    await store.load("upcoming");

    await store.loadMore();

    expect(store.items.map((item) => item.appointmentId)).toEqual(["a", "b", "c"]);
    expect(store.hasMore).toBe(false);
  });

  it("ne demande rien quand il n'y a plus de page", async () => {
    const fetchMock = vi.fn(async (path: string) => {
      void path;
      return json(page([appointment("a")], 0, 1));
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useAppointmentsStore();
    await store.load("upcoming");

    await store.loadMore();

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("garde la dernière demande quand deux onglets se chevauchent", async () => {
    let resolveUpcoming: (response: Response) => void = () => undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn((path: string) =>
        path.includes("scope=upcoming")
          ? new Promise<Response>((resolve) => {
              resolveUpcoming = resolve;
            })
          : Promise.resolve(json(page([appointment("past", "COMPLETED")], 0, 1))),
      ),
    );
    const store = useAppointmentsStore();

    const first = store.load("upcoming");
    await store.load("past");
    resolveUpcoming(json(page([appointment("late")], 0, 1)));
    await first;

    expect(store.scope).toBe("past");
    expect(store.items.map((item) => item.appointmentId)).toEqual(["past"]);
  });

  it("met la ligne à jour en place après l'annulation", async () => {
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      if (init?.method === "PATCH") {
        return json(appointment("b", "CANCELLED"));
      }
      void path;
      return json(page([appointment("a"), appointment("b"), appointment("c")], 0, 1));
    });
    vi.stubGlobal("fetch", fetchMock);
    const store = useAppointmentsStore();
    await store.load("upcoming");

    await store.cancel("b");

    const patch = fetchMock.mock.calls.find((call) => call[1]?.method === "PATCH");
    expect(patch?.[0]).toBe("/api/me/appointments/b/cancel");
    expect(patch?.[1]?.body).toBeUndefined();
    expect(store.items.map((item) => item.status)).toEqual([
      "CONFIRMED",
      "CANCELLED",
      "CONFIRMED",
    ]);
  });
});
