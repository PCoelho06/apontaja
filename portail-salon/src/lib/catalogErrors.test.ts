import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";

import {
  describeCatalogLoadError,
  describeCatalogSalonLoadError,
  describeClosureDeleteError,
  describeClosureSaveError,
  describeDeleteError,
  describeLinkError,
  describeResourceSaveError,
  describeScheduleSaveError,
  describeServiceSaveError,
  describeSettingsSaveError,
} from "@/lib/catalogErrors";

const FRENCH = "Message en français du back";

function apiError(status: number, fieldErrors?: Record<string, string>): ApiError {
  return new ApiError(status, FRENCH, fieldErrors, { detail: FRENCH });
}

describe("describeResourceSaveError", () => {
  it("traduit le champ connu, ignore les inconnus et n'affiche jamais le français", () => {
    const failure = describeResourceSaveError(
      apiError(400, { name: "Nom obligatoire", constructor: "x", autre: "y" }),
    );
    expect(Object.keys(failure.fieldErrors)).toEqual(["name"]);
    expect(failure.fieldErrors.name).toContain("nome do recurso");
    expect(failure.message).toContain("profissional");
    expect(failure.message).not.toContain("français");
  });

  it("distingue 404, 409, 403, 429 et les erreurs hors API", () => {
    expect(describeResourceSaveError(apiError(404)).message).toContain("já não existe");
    expect(describeResourceSaveError(apiError(409)).message).toContain("outro recurso");
    expect(describeResourceSaveError(apiError(403)).message).toContain("permissão");
    expect(describeResourceSaveError(apiError(429)).message).toBe(TOO_MANY_ATTEMPTS);
    expect(describeResourceSaveError(new Error("Non authentifié.")).message).toContain(
      "Não foi possível guardar",
    );
    expect(describeResourceSaveError(apiError(500)).fieldErrors).toEqual({});
  });
});

describe("describeServiceSaveError", () => {
  it("traduit les quatre champs du serviço", () => {
    const failure = describeServiceSaveError(
      apiError(400, {
        name: "a",
        description: "b",
        defaultDurationMinutes: "c",
        defaultPriceCents: "d",
      }),
    );
    expect(Object.keys(failure.fieldErrors).sort()).toEqual([
      "defaultDurationMinutes",
      "defaultPriceCents",
      "description",
      "name",
    ]);
    for (const message of Object.values(failure.fieldErrors)) {
      expect(message).not.toMatch(/^[abcd]$/);
    }
  });

  it("gère 404 et le cas générique", () => {
    expect(describeServiceSaveError(apiError(404)).message).toContain("já não existe");
    expect(describeServiceSaveError(apiError(500)).message).toContain("guardar o serviço");
  });
});

describe("describeDeleteError", () => {
  it("explique le 409 (marcações ativas) pour un recurso et un serviço", () => {
    expect(describeDeleteError(apiError(409), "resource")).toContain("marcações ativas");
    expect(describeDeleteError(apiError(409), "resource")).toContain("recurso");
    expect(describeDeleteError(apiError(409), "service")).toContain("serviço");
  });

  it("gère 404, 403 et le générique", () => {
    expect(describeDeleteError(apiError(404), "service")).toContain("já não existe");
    expect(describeDeleteError(apiError(403), "resource")).toContain("permissão");
    expect(describeDeleteError(new Error("boom"), "resource")).toContain("eliminar");
  });
});

describe("describeLinkError", () => {
  it("distingue l'enregistrement du retrait", () => {
    expect(describeLinkError(apiError(500), "save")).toContain("associar");
    expect(describeLinkError(apiError(500), "remove")).toContain("retirar");
    expect(describeLinkError(apiError(400), "save")).toContain("preço");
    expect(describeLinkError(apiError(404), "remove")).toContain("já não existe");
  });
});

describe("horaires, encerramentos et réglages", () => {
  it("traduit les 400 et les 404", () => {
    expect(describeScheduleSaveError(apiError(400))).toContain("sobrepõem");
    expect(describeScheduleSaveError(apiError(404))).toContain("já não existe");
    expect(describeClosureSaveError(apiError(400))).toContain("fim");
    expect(describeClosureSaveError(apiError(409))).toContain("marcações ativas");
    expect(describeClosureDeleteError(apiError(404))).toContain("já não existe");
    expect(describeSettingsSaveError(apiError(400))).toContain("limites");
    expect(describeSettingsSaveError(apiError(403))).toContain("permissão");
  });

  it("n'affiche jamais le texte du back", () => {
    const all = [
      describeScheduleSaveError(apiError(500)),
      describeClosureSaveError(apiError(500)),
      describeClosureDeleteError(apiError(500)),
      describeSettingsSaveError(apiError(500)),
      describeCatalogSalonLoadError(apiError(500)),
    ];
    for (const message of all) {
      expect(message).not.toContain("français");
    }
  });
});

describe("chargements", () => {
  it("donne un message par sujet et traite 403 et 404 du salon", () => {
    expect(describeCatalogLoadError(new Error("x"), "resources")).toContain("recursos");
    expect(describeCatalogLoadError(apiError(500), "services")).toContain("serviços");
    expect(describeCatalogLoadError(apiError(500), "links")).toContain("associações");
    expect(describeCatalogLoadError(apiError(500), "schedule")).toContain("horário");
    expect(describeCatalogLoadError(apiError(500), "closures")).toContain("encerramentos");
    expect(describeCatalogLoadError(apiError(500), "settings")).toContain("reserva online");
    expect(describeCatalogLoadError(apiError(403), "resources")).toContain("permissão");
    expect(describeCatalogSalonLoadError(apiError(404))).toContain("aceder");
    expect(describeCatalogSalonLoadError(apiError(403))).toContain("aceder");
  });
});

function coded(status: number, code: string): ApiError {
  return new ApiError(status, FRENCH, undefined, { detail: FRENCH, code });
}

describe("codes stables du back (5a-1)", () => {
  it("distingue les cas d'un recurso par code, avec ou sans le statut attendu", () => {
    expect(describeResourceSaveError(coded(409, "RESOURCE_NAME_ALREADY_USED")).message).toContain(
      "com este nome",
    );
    expect(describeResourceSaveError(coded(409, "STAFF_MEMBER_ALREADY_LINKED")).message).toContain(
      "outro recurso",
    );
    expect(describeResourceSaveError(coded(404, "STAFF_MEMBER_NOT_FOUND")).message).toContain(
      "membro da equipa",
    );
    expect(describeResourceSaveError(coded(404, "RESOURCE_NOT_FOUND")).message).toBe(
      "Este recurso já não existe.",
    );
    expect(describeResourceSaveError(coded(400, "RESOURCE_STAFF_LINK_INVALID")).message).toContain(
      "profissional",
    );
    expect(describeResourceSaveError(coded(400, "INVALID_RESOURCE_TYPE")).message).toContain(
      "Tipo de recurso",
    );
  });

  it("garde les champs traduits d'une validation sans code", () => {
    const failure = describeResourceSaveError(apiError(400, { name: "x" }));
    expect(Object.keys(failure.fieldErrors)).toEqual(["name"]);
  });

  it("traduit les erreurs de serviço, de suppression et d'association", () => {
    expect(describeServiceSaveError(coded(409, "SERVICE_NAME_ALREADY_USED")).message).toContain(
      "com este nome",
    );
    expect(describeServiceSaveError(coded(404, "SERVICE_NOT_FOUND")).message).toContain(
      "já não existe",
    );
    expect(describeDeleteError(coded(409, "RESOURCE_IN_USE"), "resource")).toContain(
      "recurso",
    );
    expect(describeDeleteError(coded(409, "SERVICE_IN_USE"), "service")).toContain("serviço");
    expect(describeDeleteError(coded(409, "SERVICE_IN_USE"), "resource")).toContain(
      "tem marcações ativas",
    );
    expect(describeLinkError(coded(409, "SERVICE_RESOURCE_LINK_CONFLICT"), "save")).toContain(
      "alterada entretanto",
    );
    expect(describeLinkError(coded(404, "RESOURCE_NOT_FOUND"), "remove")).toBe(
      "Este recurso já não existe.",
    );
  });

  it("traduit horários, encerramentos et définitions", () => {
    expect(describeScheduleSaveError(coded(400, "INVALID_TIME_RANGE"))).toContain("sobrepõem");
    expect(describeClosureSaveError(coded(400, "INVALID_TIME_RANGE"))).toContain("fim");
    expect(describeClosureSaveError(coded(409, "CLOSURE_BLOCKED_BY_APPOINTMENTS"))).toContain(
      "marcações ativas",
    );
    expect(describeClosureSaveError(coded(404, "CLOSURE_NOT_FOUND"))).toBe(
      "Este encerramento já não existe.",
    );
    expect(describeClosureDeleteError(coded(404, "CLOSURE_NOT_FOUND"))).toBe(
      "Este encerramento já não existe.",
    );
    expect(describeSettingsSaveError(coded(400, "INVALID_BOOKING_SETTINGS"))).toContain("limites");
    expect(describeCatalogLoadError(coded(404, "RESOURCE_NOT_FOUND"), "schedule")).toBe(
      "Este recurso já não existe.",
    );
  });

  it("ignore un code inconnu (repli sur le statut) et n'affiche jamais le français", () => {
    expect(describeResourceSaveError(coded(409, "NOUVEAU_CODE")).message).toContain("outro recurso");
    expect(describeSettingsSaveError(coded(500, "NOUVEAU_CODE"))).toContain("guardar as definições");
    expect(describeDeleteError(coded(409, "constructor"), "service")).not.toContain("français");
    expect(describeResourceSaveError(coded(429, "RESOURCE_IN_USE")).message).toBe(TOO_MANY_ATTEMPTS);
  });
});
