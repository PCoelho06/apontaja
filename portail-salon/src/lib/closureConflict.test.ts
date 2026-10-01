import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/apiClient";
import { parseClosureConflict } from "@/lib/closureConflict";

describe("parseClosureConflict", () => {
  it("extrait le nombre et la liste des RDV bloquants", () => {
    const appointment = {
      appointmentId: "a1",
      resourceId: "r1",
      startAt: "2099-01-05T09:00:00Z",
      endAt: "2099-01-05T10:00:00Z",
    };
    const error = new ApiError(409, "conflit", undefined, {
      count: 25,
      appointments: [appointment],
    });

    expect(parseClosureConflict(error)).toEqual({
      count: 25,
      appointments: [appointment],
    });
  });

  it("ignore les autres erreurs", () => {
    expect(parseClosureConflict(new ApiError(409, "autre conflit"))).toBeNull();
    expect(
      parseClosureConflict(new ApiError(400, "x", undefined, { count: 1 })),
    ).toBeNull();
    expect(parseClosureConflict(new Error("réseau"))).toBeNull();
  });
});
