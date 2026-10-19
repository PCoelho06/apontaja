import { describe, expect, it } from "vitest";

import { buildCustomerPayload } from "@/lib/customerForm";

const VALID = {
  firstName: " Alice ",
  lastName: " Martin ",
  email: " alice@example.com ",
  phone: "  ",
  internalNotes: " Allergie ",
};

describe("buildCustomerPayload", () => {
  it("normalise la saisie", () => {
    expect(buildCustomerPayload(VALID)).toEqual({
      payload: {
        firstName: "Alice",
        lastName: "Martin",
        email: "alice@example.com",
        phone: null,
        internalNotes: "Allergie",
      },
    });
  });

  it("refuse un prénom ou un nom vide", () => {
    expect(buildCustomerPayload({ ...VALID, firstName: "  " })).toHaveProperty(
      "error",
    );
    expect(buildCustomerPayload({ ...VALID, lastName: "" })).toHaveProperty(
      "error",
    );
  });

  it("refuse l'absence d'email et de téléphone", () => {
    expect(buildCustomerPayload({ ...VALID, email: " " })).toHaveProperty(
      "error",
    );
  });

  it("accepte un téléphone seul", () => {
    const result = buildCustomerPayload({
      ...VALID,
      email: "",
      phone: "0612345678",
    });
    expect(result).toHaveProperty("payload.phone", "0612345678");
  });
});
