import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";

import { describeBookingError, describeLoadError } from "@/lib/bookingErrors";

function apiError(status: number, code?: string): ApiError {
  return new ApiError(status, "Message en français du back", undefined, {
    detail: "Message en français du back",
    ...(code ? { code } : {}),
  });
}

describe("describeBookingError", () => {
  it.each([
    ["EMAIL_NOT_VERIFIED", 403],
    ["SHARING_CONSENT_REQUIRED", 400],
    ["BOOKING_QUOTA_EXCEEDED", 409],
    ["SLOT_UNAVAILABLE", 409],
  ] as const)("reconnaît le code %s", (code, status) => {
    const failure = describeBookingError(apiError(status, code));

    expect(failure.kind).toBe(code);
    expect(failure.message).not.toContain("français");
  });

  it("s'appuie sur le code avant le statut", () => {
    expect(describeBookingError(apiError(409, "SLOT_UNAVAILABLE")).kind).toBe(
      "SLOT_UNAVAILABLE",
    );
  });

  it("se rabat sur le statut HTTP sans code", () => {
    expect(describeBookingError(apiError(401)).kind).toBe("UNAUTHENTICATED");
    expect(describeBookingError(apiError(404)).kind).toBe("NOT_FOUND");
    expect(describeBookingError(apiError(429)).kind).toBe("RATE_LIMITED");
    expect(describeBookingError(apiError(400)).kind).toBe("INVALID");
    expect(describeBookingError(apiError(500)).kind).toBe("UNKNOWN");
  });

  it("n'expose jamais le message français du back", () => {
    expect(describeBookingError(apiError(500)).message).toBe(
      "Não foi possível concluir a marcação. Tente novamente.",
    );
  });

  it("traite une erreur inconnue", () => {
    expect(describeBookingError(new Error("boom")).kind).toBe("UNKNOWN");
  });
});

describe("describeLoadError", () => {
  it("distingue introuvable, trop de requêtes et le reste", () => {
    expect(describeLoadError(apiError(404))).toContain("não aceita marcações");
    expect(describeLoadError(apiError(429))).toContain("Demasiados pedidos");
    expect(describeLoadError(apiError(500))).toContain("Não foi possível carregar");
    expect(describeLoadError(new Error("x"))).toContain("Não foi possível carregar");
  });
});
