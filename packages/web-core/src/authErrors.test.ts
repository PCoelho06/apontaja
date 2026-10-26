import { describe, expect, it } from "vitest";

import { ApiError } from "./apiClient";
import {
  TOO_MANY_ATTEMPTS,
  describeConfirmError,
  describeLoginError,
  describeRegisterError,
  describeResetError,
} from "./authErrors";

const FRENCH = "Message en français du back";

function apiError(
  status: number,
  fieldErrors?: Record<string, string>,
): ApiError {
  return new ApiError(status, FRENCH, fieldErrors, { detail: FRENCH });
}

describe("describeLoginError", () => {
  it("traduit les identifiants invalides, le débit et les données invalides", () => {
    expect(describeLoginError(apiError(401))).toBe(
      "Email ou palavra-passe incorretos.",
    );
    expect(describeLoginError(apiError(429))).toBe(TOO_MANY_ATTEMPTS);
    expect(describeLoginError(apiError(400))).toContain("Verifique");
  });

  it("retombe sur un message générique, jamais sur le texte du back", () => {
    expect(describeLoginError(apiError(500))).not.toContain("français");
    expect(describeLoginError(new Error("boom"))).toContain(
      "Não foi possível iniciar sessão",
    );
  });
});

describe("describeRegisterError", () => {
  it("traduit un email déjà utilisé (409)", () => {
    expect(describeRegisterError(apiError(409))).toEqual({
      message: "Já existe uma conta com este email.",
      fieldErrors: {},
    });
  });

  it("traduit les champs connus et ignore les inconnus (400)", () => {
    const failure = describeRegisterError(
      apiError(400, {
        firstName: "Prénom obligatoire",
        lastName: "Nom obligatoire",
        email: "Email invalide",
        password: "Trop court",
        acceptTos: "Obligatoire",
      }),
    );

    expect(failure.message).toBe("Verifique os dados introduzidos.");
    expect(Object.keys(failure.fieldErrors).sort()).toEqual([
      "email",
      "firstName",
      "lastName",
      "password",
    ]);
    for (const message of Object.values(failure.fieldErrors)) {
      expect(message).not.toMatch(/obligatoire|invalide|court/i);
    }
  });

  it("gère le débit, les autres statuts et les erreurs non API", () => {
    expect(describeRegisterError(apiError(429)).message).toBe(
      TOO_MANY_ATTEMPTS,
    );
    expect(describeRegisterError(apiError(500)).message).toContain(
      "Não foi possível criar a conta",
    );
    expect(describeRegisterError(new Error("boom")).fieldErrors).toEqual({});
  });
});

describe("describeResetError", () => {
  it("traduit l'erreur de champ de la nouvelle palavra-passe", () => {
    const failure = describeResetError(
      apiError(400, { newPassword: "Trop court" }),
    );

    expect(failure.message).toBe("Verifique a nova palavra-passe.");
    expect(failure.fieldErrors.newPassword).toContain("12 caracteres");
  });

  it("traite un 400 sans champ comme un jeton invalide ou expiré", () => {
    const failure = describeResetError(apiError(400));

    expect(failure.message).toContain("inválida ou expirou");
    expect(failure.fieldErrors).toEqual({});
  });

  it("gère le débit et les autres erreurs", () => {
    expect(describeResetError(apiError(429)).message).toBe(TOO_MANY_ATTEMPTS);
    expect(describeResetError(apiError(500)).message).toContain(
      "Não foi possível repor",
    );
    expect(describeResetError(new Error("boom")).message).toContain(
      "Não foi possível repor",
    );
  });
});

describe("describeConfirmError", () => {
  it("indique un lien invalide ou expiré, sauf limitation de débit", () => {
    expect(describeConfirmError(apiError(400))).toContain("inválida ou expirou");
    expect(describeConfirmError(new Error("boom"))).toContain(
      "inválida ou expirou",
    );
    expect(describeConfirmError(apiError(429))).toBe(TOO_MANY_ATTEMPTS);
  });
});
