import { describe, expect, it } from "vitest";

import { STAFF_ROLES, formatDate, roleLabel } from "@/lib/staffRoles";

describe("roleLabel", () => {
  it("traduit les rôles connus en pt-PT", () => {
    expect(roleLabel("OWNER")).toBe("Proprietário");
    expect(roleLabel("MANAGER")).toBe("Gestor");
    expect(roleLabel("EMPLOYEE")).toBe("Colaborador");
    expect(roleLabel("ORGANIZATION_OWNER")).toBe("Proprietário da organização");
  });

  it("renvoie la valeur brute pour un rôle inconnu", () => {
    expect(roleLabel("AUTRE")).toBe("AUTRE");
  });

  it("couvre tous les rôles proposables", () => {
    for (const role of STAFF_ROLES) {
      expect(roleLabel(role)).not.toBe(role);
    }
  });
});

describe("formatDate", () => {
  it("formate au format pt-PT", () => {
    expect(formatDate("2099-06-10T10:00:00Z")).toContain("2099");
    expect(formatDate("2099-06-10T10:00:00Z")).toMatch(/10\/06\/2099|10\/6\/2099/);
  });

  it("renvoie une chaîne vide pour une date illisible", () => {
    expect(formatDate("pas-une-date")).toBe("");
  });
});
