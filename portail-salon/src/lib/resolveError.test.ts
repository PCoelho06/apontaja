import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";

import { FORBIDDEN, resolveMessage, translateFields } from "@/lib/resolveError";

const FRENCH = "Message en français du back";

function coded(status: number, code?: string): ApiError {
  return new ApiError(status, FRENCH, undefined, {
    detail: FRENCH,
    ...(code === undefined ? {} : { code }),
  });
}

const TABLE = {
  codes: new Map([["KNOWN", "Por código."]]),
  statuses: new Map([
    [404, "Por estado 404."],
    [409, "Por estado 409."],
  ]),
  generic: "Genérico.",
};

describe("resolveMessage", () => {
  it("donne le message générique pour une erreur qui n'est pas une ApiError", () => {
    expect(resolveMessage(new Error(FRENCH), TABLE)).toBe("Genérico.");
    expect(resolveMessage("x", TABLE)).toBe("Genérico.");
  });

  it("préfère le code connu au statut", () => {
    expect(resolveMessage(coded(409, "KNOWN"), TABLE)).toBe("Por código.");
    expect(resolveMessage(coded(404, "KNOWN"), TABLE)).toBe("Por código.");
  });

  it("se rabat sur le statut si le code est absent ou inconnu", () => {
    expect(resolveMessage(coded(409), TABLE)).toBe("Por estado 409.");
    expect(resolveMessage(coded(409, "AUTRE"), TABLE)).toBe("Por estado 409.");
    expect(resolveMessage(coded(500, "AUTRE"), TABLE)).toBe("Genérico.");
  });

  it("n'atteint jamais un prototype avec un code arbitraire", () => {
    for (const code of ["constructor", "__proto__", "toString"]) {
      expect(resolveMessage(coded(409, code), TABLE)).toBe("Por estado 409.");
    }
  });

  it("traite 429 et 403 de façon commune, 403 surchargeable", () => {
    expect(resolveMessage(coded(429, "KNOWN"), TABLE)).toBe(TOO_MANY_ATTEMPTS);
    expect(resolveMessage(coded(403), TABLE)).toBe(FORBIDDEN);
    expect(resolveMessage(coded(403), { ...TABLE, statuses: new Map([[403, "Seu."]]) })).toBe(
      "Seu.",
    );
  });

  it("fonctionne sans table de codes ni de statuts", () => {
    expect(resolveMessage(coded(409, "KNOWN"), { generic: "G." })).toBe("G.");
  });
});

describe("translateFields", () => {
  it("ne garde que les champs connus", () => {
    const error = new ApiError(400, FRENCH, { name: "a", constructor: "b", autre: "c" });

    expect(translateFields(error, new Map([["name", "Nome."]]))).toEqual({ name: "Nome." });
    expect(translateFields(new Error("x"), new Map([["name", "Nome."]]))).toEqual({});
  });
});
