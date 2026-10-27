import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";
import {
  describeRequestActionError,
  describeRequestsLoadError,
  shouldReloadRequests,
} from "@/lib/bookingRequestErrors";

const FRENCH = "Transition interdite côté back";

describe("describeRequestsLoadError", () => {
  it("traduit par opération et statut", () => {
    expect(describeRequestsLoadError(new ApiError(403, FRENCH), "load")).toContain(
      "permissão",
    );
    expect(describeRequestsLoadError(new ApiError(404, FRENCH), "load")).toContain(
      "salão",
    );
    expect(describeRequestsLoadError(new ApiError(500, FRENCH), "load")).toBe(
      "Não foi possível carregar os pedidos.",
    );
    expect(describeRequestsLoadError(new Error("réseau"), "refresh")).toBe(
      "Não foi possível atualizar os pedidos.",
    );
    expect(describeRequestsLoadError(new ApiError(429, FRENCH), "load")).toBe(
      TOO_MANY_ATTEMPTS,
    );
  });
});

describe("describeRequestActionError", () => {
  it("explique qu'un pedido a changé pour 404 et 409", () => {
    for (const status of [404, 409]) {
      const message = describeRequestActionError(new ApiError(status, FRENCH), "confirm");
      expect(message).toContain("mudou entretanto");
      expect(message).not.toContain("Transition");
    }
  });

  it("donne un repli par action", () => {
    expect(describeRequestActionError(new ApiError(500, FRENCH), "confirm")).toBe(
      "Não foi possível confirmar o pedido.",
    );
    expect(describeRequestActionError(new Error("x"), "decline")).toBe(
      "Não foi possível recusar o pedido.",
    );
    expect(describeRequestActionError(new ApiError(403, FRENCH), "decline")).toContain(
      "permissão",
    );
  });

  it("recharge après 404 et 409 seulement", () => {
    expect(shouldReloadRequests(new ApiError(409, FRENCH))).toBe(true);
    expect(shouldReloadRequests(new ApiError(404, FRENCH))).toBe(true);
    expect(shouldReloadRequests(new ApiError(500, FRENCH))).toBe(false);
    expect(shouldReloadRequests(new Error("x"))).toBe(false);
  });
});
