import { describe, expect, it } from "vitest";

import { contactLine, fullName, visitLabel } from "@/lib/customerFormat";

describe("customerFormat", () => {
  it("assemble le nom complet", () => {
    expect(fullName({ firstName: "Alice", lastName: "Martin" })).toBe(
      "Alice Martin",
    );
  });

  it("assemble les moyens de contact présents", () => {
    expect(contactLine({ email: "a@example.com", phone: "0612345678" })).toBe(
      "a@example.com · 0612345678",
    );
    expect(contactLine({ email: null, phone: "0612345678" })).toBe(
      "0612345678",
    );
    expect(contactLine({ email: undefined, phone: null })).toBe("");
  });

  it("affiche un tiret quand il n'y a pas de visite", () => {
    expect(visitLabel(null, "Europe/Paris")).toBe("—");
    expect(visitLabel(undefined, "Europe/Paris")).toBe("—");
  });

  it("renvoie l'instant brut quand le fuseau est invalide", () => {
    expect(visitLabel("2020-03-05T14:00:00Z", "Paris")).toBe(
      "2020-03-05T14:00:00Z",
    );
  });
});
