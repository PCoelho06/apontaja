import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/apiClient";
import { describeIssues } from "@/lib/availabilityIssues";

describe("describeIssues", () => {
  it("traduit les problèmes de disponibilité connus et laisse passer les inconnus", () => {
    const error = new ApiError(409, "indisponible", undefined, {
      issues: ["OUTSIDE_SALON_HOURS", "RESOURCE_CLOSED", "AUTRE"],
    });

    expect(describeIssues(error)).toEqual([
      "Ce créneau est en dehors des horaires du salon.",
      "La ressource est fermée sur ce créneau.",
      "AUTRE",
    ]);
  });

  it("renvoie un tableau vide pour les autres erreurs", () => {
    expect(describeIssues(new ApiError(409, "créneau pris"))).toEqual([]);
    expect(
      describeIssues(new ApiError(400, "x", undefined, { issues: ["SALON_CLOSED"] })),
    ).toEqual([]);
    expect(describeIssues(new Error("réseau"))).toEqual([]);
  });
});
