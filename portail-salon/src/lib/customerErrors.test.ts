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

function coded(status: number, code: string): ApiError {
  return new ApiError(status, FRENCH, undefined, { detail: FRENCH, code });
}

describe("codes stables du back (5a-1)", () => {
  it("rend l'anonymisation précise par code", () => {
    expect(describeAnonymizeError(coded(409, "CUSTOMER_SHARED"))).toContain("outro salão");
    expect(describeAnonymizeError(coded(409, "CUSTOMER_SHARED"))).toContain("conta de cliente");
    expect(describeAnonymizeError(coded(409, "CUSTOMER_SHARED"))).not.toContain("marcações futuras");
    expect(describeAnonymizeError(coded(409, "CUSTOMER_HAS_UPCOMING_APPOINTMENTS"))).toContain(
      "marcações futuras",
    );
    expect(describeAnonymizeError(coded(409, "CUSTOMER_ANONYMIZED"))).toBe(
      "Este cliente já foi anonimizado.",
    );
    expect(describeAnonymizeError(coded(404, "CUSTOMER_NOT_FOUND"))).toContain("já não existe");
  });

  it("garde le message combiné du 409 sans code ou avec un code inconnu", () => {
    expect(describeAnonymizeError(coded(409, "NOUVEAU_CODE"))).toContain("ou tem marcações futuras");
    expect(describeAnonymizeError(coded(403, "constructor"))).toContain("gestores e proprietários");
  });

  it("traduit sauvegarde, consentement et historique par code", () => {
    expect(describeCustomerSaveError(coded(409, "CUSTOMER_ANONYMIZED")).message).toContain(
      "anonimizado",
    );
    expect(describeCustomerSaveError(coded(400, "CUSTOMER_CONTACT_REQUIRED")).message).toContain(
      "email ou um telefone",
    );
    expect(describeCustomerSaveError(coded(404, "CUSTOMER_NOT_FOUND")).fieldErrors).toEqual({});
    expect(describeConsentError(coded(409, "CUSTOMER_ANONYMIZED"))).toContain("consentimento");
    expect(describeCustomerLoadError(coded(404, "CUSTOMER_NOT_FOUND"), "history")).toBe(
      "Este cliente já não existe.",
    );
  });

  it("distingue les motifs d'échec de la fusion", () => {
    expect(describeMergeError(coded(400, "INVALID_MERGE_REQUEST"))).toContain("Seleção inválida");
    expect(describeMergeError(coded(404, "CUSTOMER_NOT_FOUND"))).toContain("atualizada");
    expect(describeMergeError(coded(409, "CUSTOMER_ANONYMIZED"))).toContain("anonimizado");
    expect(describeMergeError(coded(409, "CUSTOMER_CLAIMED"))).toContain("perfil mantido");
    expect(describeMergeError(coded(409, "CUSTOMER_SHARED"))).toContain("partilhado");
    expect(describeMergeError(coded(409, "NOUVEAU_CODE"))).toContain("Fusão impossível");
    for (const code of ["CUSTOMER_CLAIMED", "CUSTOMER_SHARED", "CUSTOMER_ANONYMIZED"]) {
      expect(shouldReloadAfterMerge(coded(409, code))).toBe(true);
    }
  });
});
