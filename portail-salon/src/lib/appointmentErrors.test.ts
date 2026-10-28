import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";
import {
  describeAgendaLoadError,
  describeCreateError,
  describeCustomerSearchError,
  describeLocalTimeError,
  describeNewCustomerError,
  describeRescheduleError,
  describeStatusActionError,
  failureLines,
  shouldReloadAfter,
} from "@/lib/appointmentErrors";

const FRENCH = "Créneau indisponible côté back";

describe("describeAgendaLoadError", () => {
  it("traduit par statut et ignore le message du back", () => {
    expect(describeAgendaLoadError(new ApiError(403, FRENCH))).toContain(
      "permissão",
    );
    expect(describeAgendaLoadError(new ApiError(404, FRENCH))).toContain(
      "salão",
    );
    expect(describeAgendaLoadError(new ApiError(500, FRENCH))).toBe(
      "Não foi possível carregar a agenda.",
    );
  });

  it("donne le message générique pour une erreur qui n'est pas une ApiError", () => {
    expect(describeAgendaLoadError(new Error("Failed to fetch"))).toBe(
      "Não foi possível carregar a agenda.",
    );
  });

  it("traduit le 429", () => {
    expect(describeAgendaLoadError(new ApiError(429, FRENCH))).toBe(
      TOO_MANY_ATTEMPTS,
    );
  });
});

describe("describeCreateError / describeRescheduleError", () => {
  it("renvoie les motifs d'indisponibilité d'un 409 avec issues", () => {
    const error = new ApiError(409, FRENCH, undefined, {
      issues: ["SALON_CLOSED"],
    });

    const created = describeCreateError(error);
    expect(created.issues).toEqual(["O salão está encerrado neste horário."]);
    expect(failureLines(created)).toEqual(created.issues);
    expect(describeRescheduleError(error).issues).toEqual(created.issues);
  });

  it("traduit un 409 sans issues comme un conflit", () => {
    const created = describeCreateError(new ApiError(409, FRENCH));
    expect(created.issues).toEqual([]);
    expect(created.message).toContain("Já existe uma marcação");
    expect(failureLines(created)).toEqual([created.message]);

    expect(describeRescheduleError(new ApiError(409, FRENCH)).message).toContain(
      "Atualize a agenda",
    );
  });

  it("traduit 400, 403, 404 et le cas générique sans jamais montrer le texte du back", () => {
    const all = [400, 403, 404, 500].flatMap((status) => [
      describeCreateError(new ApiError(status, FRENCH)).message,
      describeRescheduleError(new ApiError(status, FRENCH)).message,
    ]);

    for (const message of all) {
      expect(message).not.toContain("Créneau");
      expect(message).toMatch(/\.$/);
    }
    expect(describeCreateError(new ApiError(403, FRENCH)).message).toBe(
      "Não tem permissão para esta ação.",
    );
    expect(describeRescheduleError(new ApiError(404, FRENCH)).message).toBe(
      "Esta marcação já não existe.",
    );
    expect(describeCreateError(new Error("réseau")).message).toBe(
      "Não foi possível criar a marcação. Tente novamente.",
    );
  });
});

describe("describeStatusActionError", () => {
  it("traduit un 409 en transition devenue impossible", () => {
    expect(
      describeStatusActionError(new ApiError(409, FRENCH), "confirm"),
    ).toBe("O estado desta marcação mudou. Atualize a agenda.");
  });

  it("traduit un 404 et donne un message par action sinon", () => {
    expect(
      describeStatusActionError(new ApiError(404, FRENCH), "cancel"),
    ).toBe("Esta marcação já não existe.");
    expect(describeStatusActionError(new Error("x"), "confirm")).toContain(
      "confirmar",
    );
    expect(describeStatusActionError(new Error("x"), "complete")).toContain(
      "concluir",
    );
    expect(describeStatusActionError(new Error("x"), "no-show")).toContain(
      "falta",
    );
    expect(describeStatusActionError(new ApiError(500, FRENCH), "cancel")).toContain(
      "cancelar",
    );
  });
});

describe("shouldReloadAfter", () => {
  it("recharge pour 404 et 409 seulement", () => {
    expect(shouldReloadAfter(new ApiError(404, "x"))).toBe(true);
    expect(shouldReloadAfter(new ApiError(409, "x"))).toBe(true);
    expect(shouldReloadAfter(new ApiError(500, "x"))).toBe(false);
    expect(shouldReloadAfter(new Error("x"))).toBe(false);
  });
});

describe("clients dans le dialogue de marcação", () => {
  it("traduit la création à la volée et la recherche", () => {
    expect(describeNewCustomerError(new ApiError(400, FRENCH))).toContain(
      "email ou um telefone",
    );
    expect(describeNewCustomerError(new ApiError(500, FRENCH))).toBe(
      "Não foi possível criar o cliente. Tente novamente.",
    );
    expect(describeCustomerSearchError(new ApiError(500, FRENCH))).toBe(
      "Não foi possível pesquisar os clientes.",
    );
  });
});

describe("describeLocalTimeError", () => {
  it("garde le message pt-PT d'une erreur locale de conversion", () => {
    expect(describeLocalTimeError(new Error("Data e hora inválidas."))).toBe(
      "Data e hora inválidas.",
    );
  });

  it("n'affiche pas le message d'une ApiError ni d'une valeur inconnue", () => {
    expect(describeLocalTimeError(new ApiError(400, FRENCH))).toBe(
      "Data e hora inválidas.",
    );
    expect(describeLocalTimeError("x", "Repli.")).toBe("Repli.");
  });
});

function coded(status: number, code: string, extra: Record<string, unknown> = {}): ApiError {
  return new ApiError(status, FRENCH, undefined, { detail: FRENCH, code, ...extra });
}

describe("codes stables du back (5a-1)", () => {
  it("distingue les 404 de la création", () => {
    expect(describeCreateError(coded(404, "RESOURCE_NOT_FOUND")).message).toContain("recurso");
    expect(describeCreateError(coded(404, "SERVICE_NOT_FOUND")).message).toContain("serviço");
    expect(describeCreateError(coded(404, "CUSTOMER_NOT_FOUND")).message).toContain("anonimizado");
    expect(describeCreateError(coded(400, "SERVICE_RESOURCE_NOT_ASSOCIATED")).message).toContain(
      "não realiza",
    );
    expect(describeCreateError(coded(400, "INVALID_TIME_RANGE")).message).toBe(
      "Data e hora inválidas.",
    );
  });

  it("garde les motifs issues[] et traduit les 409 par code", () => {
    const withIssues = coded(409, "APPOINTMENT_NOT_AVAILABLE", { issues: ["RESOURCE_CLOSED"] });
    expect(describeCreateError(withIssues).issues).toEqual(["O recurso está encerrado neste horário."]);
    expect(describeRescheduleError(withIssues).issues).toHaveLength(1);

    const noIssues = describeCreateError(coded(409, "APPOINTMENT_NOT_AVAILABLE"));
    expect(noIssues).toEqual({ issues: [], message: "Este horário não está disponível." });
    expect(describeCreateError(coded(409, "APPOINTMENT_CONFLICT")).message).toContain(
      "Já existe uma marcação",
    );
  });

  it("traduit le déplacement et les transitions par code", () => {
    expect(describeRescheduleError(coded(409, "APPOINTMENT_NOT_RESCHEDULABLE")).message).toContain(
      "já não pode ser alterada",
    );
    expect(describeRescheduleError(coded(409, "APPOINTMENT_CONFLICT")).message).toContain(
      "ocupado entretanto",
    );
    expect(describeRescheduleError(coded(404, "APPOINTMENT_NOT_FOUND")).message).toBe(
      "Esta marcação já não existe.",
    );
    expect(describeStatusActionError(coded(409, "APPOINTMENT_TRANSITION_FORBIDDEN"), "complete")).toBe(
      "O estado desta marcação mudou. Atualize a agenda.",
    );
    expect(describeStatusActionError(coded(404, "APPOINTMENT_NOT_FOUND"), "cancel")).toBe(
      "Esta marcação já não existe.",
    );
  });

  it("traduit le contact obligatoire d'un cliente créé à la volée", () => {
    expect(describeNewCustomerError(coded(400, "CUSTOMER_CONTACT_REQUIRED"))).toContain(
      "email ou um telefone",
    );
  });

  it("se rabat sur le statut pour un code inconnu, sans afficher le français", () => {
    expect(describeCreateError(coded(409, "NOUVEAU_CODE")).message).toContain("Já existe uma marcação");
    expect(describeStatusActionError(coded(500, "constructor"), "confirm")).toContain("confirmar");
    expect(describeCreateError(coded(409, "NOUVEAU_CODE")).message).not.toContain("Créneau");
  });

  it("recharge toujours d'après le statut (404/409), pas le code", () => {
    expect(shouldReloadAfter(coded(409, "APPOINTMENT_CONFLICT"))).toBe(true);
    expect(shouldReloadAfter(coded(400, "INVALID_TIME_RANGE"))).toBe(false);
  });
});
