import { describe, expect, it } from "vitest";

import { ApiError } from "./apiClient";
import { errorMessage } from "./errorMessage";

describe("errorMessage", () => {
  it("renvoie le message métier d'une ApiError", () => {
    const error = new ApiError(409, "Ce créneau n'est plus disponible.");

    expect(errorMessage(error, "repli")).toBe("Ce créneau n'est plus disponible.");
  });

  it("renvoie le repli pour une erreur inconnue", () => {
    expect(errorMessage(new Error("technique"), "repli")).toBe("repli");
    expect(errorMessage("texte", "repli")).toBe("repli");
    expect(errorMessage(undefined, "repli")).toBe("repli");
  });
});
