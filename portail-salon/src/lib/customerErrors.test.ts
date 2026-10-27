import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";
import {
  describeAnonymizeError,
  describeConsentError,
  describeCustomerLoadError,
  describeCustomerSaveError,
  describeMergeError,
  shouldReloadAfterMerge,
} from "@/lib/customerErrors";

const FRENCH = "Profil introuvable côté back";

describe("describeCustomerLoadError", () => {
  it("traduit par sujet et statut sans jamais afficher le message du back", () => {
    expect(describeCustomerLoadError(new ApiError(403, FRENCH), "list")).toContain(
      "permissão",
    );
    expect(describeCustomerLoadError(new ApiError(404, FRENCH), "history")).toBe(
      "Este cliente já não existe.",
    );
    expect(
      describeCustomerLoadError(new ApiError(403, FRENCH), "duplicates"),
    ).toContain("gestores e proprietários");
    expect(describeCustomerLoadError(new ApiError(404, FRENCH), "salon")).toContain(
      "salão",
    );
    expect(describeCustomerLoadError(new ApiError(500, FRENCH), "list")).toBe(
      "Não foi possível carregar os clientes.",
    );
  });

  it("donne le message générique pour une erreur qui n'est pas une ApiError", () => {
    expect(describeCustomerLoadError(new TypeError("Failed to fetch"), "history")).toBe(
      "Não foi possível carregar o histórico.",
    );
  });

  it("traduit le 429", () => {
    expect(describeCustomerLoadError(new ApiError(429, FRENCH), "list")).toBe(
      TOO_MANY_ATTEMPTS,
    );
  });
});

describe("describeCustomerSaveError", () => {
  it("traduit les champs connus et ignore les inconnus", () => {
    const result = describeCustomerSaveError(
      new ApiError(400, FRENCH, {
        firstName: "ne doit pas être vide",
        constructor: "x",
        autre: "y",
      }),
    );
    expect(result.fieldErrors).toEqual({
      firstName: "Indique o nome próprio (100 caracteres no máximo).",
    });
    expect(result.message).toContain("email ou um telefone");
    expect(JSON.stringify(result)).not.toContain("vide");
  });

  it("traduit 404, 409 et le repli", () => {
    expect(describeCustomerSaveError(new ApiError(404, FRENCH)).message).toContain(
      "já não existe",
    );
    expect(describeCustomerSaveError(new ApiError(409, FRENCH)).message).toContain(
      "anonimizado",
    );
    expect(describeCustomerSaveError(new Error("réseau"))).toEqual({
      message: "Não foi possível guardar o cliente. Tente novamente.",
      fieldErrors: {},
    });
  });
});

describe("describeConsentError", () => {
  it("traduit 404, 409 et le repli", () => {
    expect(describeConsentError(new ApiError(404, FRENCH))).toContain("já não existe");
    expect(describeConsentError(new ApiError(409, FRENCH))).toContain("anonimizado");
    expect(describeConsentError(new ApiError(500, FRENCH))).toBe(
      "Não foi possível atualizar o consentimento.",
    );
  });
});

describe("describeAnonymizeError", () => {
  it("traduit 403, 404, 409 et le repli", () => {
    expect(describeAnonymizeError(new ApiError(403, FRENCH))).toContain(
      "gestores e proprietários",
    );
    expect(describeAnonymizeError(new ApiError(404, FRENCH))).toContain("já não existe");
    expect(describeAnonymizeError(new ApiError(409, FRENCH))).toContain(
      "marcações futuras",
    );
    expect(describeAnonymizeError(new Error("x"))).toBe(
      "Não foi possível anonimizar o cliente.",
    );
  });
});

describe("describeMergeError / shouldReloadAfterMerge", () => {
  it("traduit 400, 403, 404, 409 et le repli", () => {
    expect(describeMergeError(new ApiError(400, FRENCH))).toContain("Seleção inválida");
    expect(describeMergeError(new ApiError(403, FRENCH))).toContain("fundir");
    expect(describeMergeError(new ApiError(404, FRENCH))).toContain("atualizada");
    expect(describeMergeError(new ApiError(409, FRENCH))).toContain("Fusão impossível");
    expect(describeMergeError(new ApiError(500, FRENCH))).toBe("A fusão falhou.");
  });

  it("recharge après 404 et 409 seulement", () => {
    expect(shouldReloadAfterMerge(new ApiError(404, FRENCH))).toBe(true);
    expect(shouldReloadAfterMerge(new ApiError(409, FRENCH))).toBe(true);
    expect(shouldReloadAfterMerge(new ApiError(400, FRENCH))).toBe(false);
    expect(shouldReloadAfterMerge(new Error("x"))).toBe(false);
  });
});
