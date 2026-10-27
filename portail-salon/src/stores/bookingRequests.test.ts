import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet, apiPatch } from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";
import { useBookingRequestsStore } from "@/stores/bookingRequests";

vi.mock("@apontaja/web-core/api-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@apontaja/web-core/api-client")>();
  return { ...actual, apiGet: vi.fn(), apiPatch: vi.fn() };
});

const NOW = new Date("2099-06-01T10:00:00Z");

function item(id: string, status: string, startAt: string) {
  return {
    appointmentId: id,
    salonId: "s1",
    customerProfileId: "c",
    serviceId: "svc",
    resourceId: "r",
    startAt,
    endAt: startAt,
    status,
    priceAtBookingCents: 1000,
    durationAtBookingMinutes: 30,
    createdAt: "2099-05-30T10:00:00Z",
  };
}

describe("bookingRequests store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    useAuthStore().accessToken = "tok";
  });

  it("charge les demandes SCHEDULED à venir avec une fenêtre bornée", async () => {
    vi.mocked(apiGet).mockResolvedValue([
      item("b", "SCHEDULED", "2099-06-10T10:00:00Z"),
      item("a", "SCHEDULED", "2099-06-05T10:00:00Z"),
      item("x", "CONFIRMED", "2099-06-06T10:00:00Z"),
    ]);
    const store = useBookingRequestsStore();

    await store.fetchRequests("s1", NOW);

    const url = vi.mocked(apiGet).mock.calls[0][0];
    expect(url).toContain("/api/salons/s1/appointments?status=SCHEDULED");
    expect(url).toContain(`from=${encodeURIComponent(NOW.toISOString())}`);
    expect(url).toContain("&to=");
    expect(store.requests.map((r) => r.appointmentId)).toEqual(["a", "b"]);
    expect(store.count).toBe(2);
  });

  it("confirme via PATCH confirm puis retire la demande", async () => {
    vi.mocked(apiGet).mockResolvedValue([
      item("a", "SCHEDULED", "2099-06-05T10:00:00Z"),
    ]);
    vi.mocked(apiPatch).mockResolvedValue({});
    const store = useBookingRequestsStore();
    await store.fetchRequests("s1", NOW);

    await store.confirmRequest("s1", "a");

    expect(apiPatch).toHaveBeenCalledWith(
      "/api/salons/s1/appointments/a/confirm",
      undefined,
      "tok",
    );
    expect(store.count).toBe(0);
  });

  it("refuse via PATCH cancel avec le motif fixe puis retire la demande", async () => {
    vi.mocked(apiGet).mockResolvedValue([
      item("a", "SCHEDULED", "2099-06-05T10:00:00Z"),
    ]);
    vi.mocked(apiPatch).mockResolvedValue({});
    const store = useBookingRequestsStore();
    await store.fetchRequests("s1", NOW);

    await store.declineRequest("s1", "a");

    expect(apiPatch).toHaveBeenCalledWith(
      "/api/salons/s1/appointments/a/cancel",
      { reason: "Pedido recusado pelo salão" },
      "tok",
    );
    expect(store.count).toBe(0);
  });

  it("garde la demande si l'action échoue", async () => {
    vi.mocked(apiGet).mockResolvedValue([
      item("a", "SCHEDULED", "2099-06-05T10:00:00Z"),
    ]);
    vi.mocked(apiPatch).mockRejectedValue(new Error("409"));
    const store = useBookingRequestsStore();
    await store.fetchRequests("s1", NOW);

    await expect(store.confirmRequest("s1", "a")).rejects.toThrow();

    expect(store.count).toBe(1);
  });
});



