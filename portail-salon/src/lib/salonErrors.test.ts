import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";

import {
  describeAcceptInvitationError,
  describeCreateSalonError,
  describeInviteError,
  describeMemberActionError,
  describeSalonLoadError,
  describeSalonsLoadError,
} from "@/lib/salonErrors";

const FRENCH = "Message en français du back";

function apiError(status: number, fieldErrors?: Record<string, string>): ApiError {
  return new ApiError(status, FRENCH, fieldErrors, { detail: FRENCH });
}

describe("describeCreateSalonError", () => {
  it("traduit les champs connus et ignore les inconnus", () => {
    const failure = describeCreateSalonError(
      apiError(400, { name: "Nom obligatoire", timezone: "Invalide", autre: "x" }),
    );

    expect(failure.message).toBe("Verifique os dados introduzidos.");
    expect(Object.keys(failure.fieldErrors).sort()).toEqual(["name", "timezone"]);
    for (const message of Object.values(failure.fieldErrors)) {
      expect(message).not.toMatch(/obligatoire|invalide/i);
    }
  });

  it("gère le débit, le conflit et les erreurs non API", () => {
    expect(describeCreateSalonError(apiError(429)).message).toBe(TOO_MANY_ATTEMPTS);
    expect(describeCreateSalonError(apiError(409)).message).toContain("organização");
    expect(describeCreateSalonError(apiError(500)).message).toContain("Não foi possível criar");
    expect(describeCreateSalonError(new Error("boom")).fieldErrors).toEqual({});
  });
});

describe("chargement", () => {
  it("n'affiche jamais le texte du back", () => {
    expect(describeSalonsLoadError()).toContain("salões");
    expect(describeSalonLoadError(apiError(403))).toContain("aceder");
    expect(describeSalonLoadError(apiError(404))).toContain("aceder");
    expect(describeSalonLoadError(apiError(500))).not.toContain("français");
    expect(describeSalonLoadError(new Error("boom"))).toContain("carregar");
  });
});

describe("describeInviteError", () => {
  it("traduit par statut et par champ", () => {
    expect(describeInviteError(apiError(400, { email: "Invalide" }))).toContain("email válido");
    expect(describeInviteError(apiError(400))).toContain("função");
    expect(describeInviteError(apiError(403))).toContain("permissão");
    expect(describeInviteError(apiError(409))).toContain("pendente");
    expect(describeInviteError(apiError(429))).toBe(TOO_MANY_ATTEMPTS);
    expect(describeInviteError(apiError(500))).toContain("Não foi possível enviar");
    expect(describeInviteError(new Error("boom"))).toContain("Não foi possível enviar");
  });
});

describe("describeMemberActionError", () => {
  it("traduit par statut", () => {
    expect(describeMemberActionError(apiError(403))).toContain("permissão");
    expect(describeMemberActionError(apiError(404))).toContain("equipa");
    expect(describeMemberActionError(apiError(409))).toContain("último proprietário");
    expect(describeMemberActionError(apiError(500))).toContain("Não foi possível concluir");
    expect(describeMemberActionError(new Error("boom"))).toContain("Não foi possível concluir");
  });
});

describe("describeAcceptInvitationError", () => {
  it("traduit par statut, jamais le texte du back", () => {
    expect(describeAcceptInvitationError(apiError(400))).toContain("inválido ou expirou");
    expect(describeAcceptInvitationError(apiError(403))).toContain("email diferente");
    expect(describeAcceptInvitationError(apiError(409))).toContain("Já faz parte");
    expect(describeAcceptInvitationError(apiError(429))).toBe(TOO_MANY_ATTEMPTS);
    expect(describeAcceptInvitationError(apiError(500))).not.toContain("français");
    expect(describeAcceptInvitationError(new Error("boom"))).toContain("Não foi possível aceitar");
  });
});
