import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";

import {
  describeCancelError,
  describeListError,
  isStaleView,
} from "@/lib/appointmentErrors";

function problem(status: number, code?: string): ApiError {
  return new ApiError(status, "Message français du back.", undefined, code ? { code } : {});
}

describe("describeCancelError", () => {
  it("s'appuie sur le code, jamais sur le message du back", () => {
    const notAllowed = describeCancelError(problem(409, "CANCELLATION_NOT_ALLOWED"));
    const deadline = describeCancelError(problem(409, "CANCELLATION_DEADLINE_PASSED"));

    expect(notAllowed.kind).toBe("CANCELLATION_NOT_ALLOWED");
    expect(notAllowed.message).toBe("Esta marcação já não pode ser cancelada.");
    expect(deadline.kind).toBe("CANCELLATION_DEADLINE_PASSED");
    expect(deadline.message).toContain("prazo de cancelamento");
    expect(deadline.message).not.toContain("français");
  });

  it("retombe sur le statut HTTP", () => {
    expect(describeCancelError(problem(401)).kind).toBe("UNAUTHENTICATED");
    expect(describeCancelError(problem(404)).kind).toBe("NOT_FOUND");
    expect(describeCancelError(problem(429)).kind).toBe("RATE_LIMITED");
    expect(describeCancelError(problem(500)).kind).toBe("UNKNOWN");
  });

  it("gère une erreur qui n'est pas une ApiError", () => {
    expect(describeCancelError(new Error("réseau")).kind).toBe("UNKNOWN");
  });

  it("ignore un code inconnu, même hérité (constructor)", () => {
    expect(describeCancelError(problem(409, "constructor")).kind).toBe("UNKNOWN");
  });
});

describe("isStaleView", () => {
  it("recharge pour les erreurs dues à un état périmé seulement", () => {
    expect(isStaleView("CANCELLATION_NOT_ALLOWED")).toBe(true);
    expect(isStaleView("CANCELLATION_DEADLINE_PASSED")).toBe(true);
    expect(isStaleView("NOT_FOUND")).toBe(true);
    expect(isStaleView("RATE_LIMITED")).toBe(false);
    expect(isStaleView("UNKNOWN")).toBe(false);
  });
});

describe("describeListError", () => {
  it("distingue la limite de débit", () => {
    expect(describeListError(problem(429))).toContain("Demasiados pedidos");
    expect(describeListError(problem(500))).toContain("carregar as suas marcações");
  });
});
