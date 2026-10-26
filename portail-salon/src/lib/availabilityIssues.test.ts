import { describe, expect, it } from "vitest";

import { ApiError } from "@apontaja/web-core/api-client";
import { describeIssues } from "@/lib/availabilityIssues";

describe("describeIssues", () => {
  it("traduit les problèmes connus et remplace les inconnus par un message générique", () => {
    const error = new ApiError(409, "indisponible", undefined, {
      issues: ["OUTSIDE_SALON_HOURS", "RESOURCE_CLOSED", "AUTRE"],
    });

    expect(describeIssues(error)).toEqual([
      "Este horário está fora do horário de funcionamento do salão.",
      "O recurso está encerrado neste horário.",
      "Este horário não está disponível.",
    ]);
  });

  it("traduit les quatre codes du back", () => {
    const error = new ApiError(409, "x", undefined, {
      issues: [
        "OUTSIDE_SALON_HOURS",
        "OUTSIDE_RESOURCE_HOURS",
        "SALON_CLOSED",
        "RESOURCE_CLOSED",
      ],
    });

    expect(describeIssues(error)).toEqual([
      "Este horário está fora do horário de funcionamento do salão.",
      "Este horário está fora do horário do recurso.",
      "O salão está encerrado neste horário.",
      "O recurso está encerrado neste horário.",
    ]);
  });

  it("ne répète pas un même message", () => {
    const error = new ApiError(409, "x", undefined, {
      issues: ["INCONNU_1", "INCONNU_2", "constructor"],
    });

    expect(describeIssues(error)).toEqual(["Este horário não está disponível."]);
  });

  it("renvoie un tableau vide pour les autres erreurs", () => {
    expect(describeIssues(new ApiError(409, "créneau pris"))).toEqual([]);
    expect(
      describeIssues(new ApiError(400, "x", undefined, { issues: ["SALON_CLOSED"] })),
    ).toEqual([]);
    expect(describeIssues(new Error("réseau"))).toEqual([]);
  });
});
