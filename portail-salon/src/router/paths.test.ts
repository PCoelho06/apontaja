import { describe, expect, it } from "vitest";

import router from "@/router";

const SALON = { salonId: "abc" };

describe("chemins pt-PT du portail salon (4h-1)", () => {
  it("garde les noms de routes et renomme les chemins de la zone authentification et socle", () => {
    const expected: Array<[string, Record<string, string>, string]> = [
      ["login", {}, "/entrar"],
      ["register", {}, "/registar"],
      ["forgot-password", {}, "/recuperar-palavra-passe"],
      ["reset-password", {}, "/repor-palavra-passe"],
      ["confirm-email", {}, "/confirmar-email"],
      ["terms", {}, "/termos"],
      ["privacy", {}, "/privacidade"],
      ["accept-invitation", {}, "/convites/aceitar"],
      ["create-salon", {}, "/saloes/novo"],
      ["salon-detail", SALON, "/saloes/abc"],
      ["home", {}, "/"],
    ];

    for (const [name, params, path] of expected) {
      expect(router.resolve({ name, params }).path).toBe(path);
    }
  });

  it("utilise le préfixe /saloes pour les routes des zones suivantes, suffixes inchangés", () => {
    const expected: Array<[string, string]> = [
      ["catalog-resources", "/saloes/abc/catalogue/ressources"],
      ["catalog-services", "/saloes/abc/catalogue/prestations"],
      ["catalog-schedule", "/saloes/abc/catalogue/horaires"],
      ["catalog-closures", "/saloes/abc/catalogue/fermetures"],
      ["booking-settings", "/saloes/abc/catalogue/reservation"],
      ["agenda", "/saloes/abc/agenda"],
      ["booking-requests", "/saloes/abc/demandes"],
      ["customers", "/saloes/abc/clients"],
      ["customer-duplicates", "/saloes/abc/clients/doublons"],
    ];

    for (const [name, path] of expected) {
      expect(router.resolve({ name, params: SALON }).path).toBe(path);
    }
  });

  it("n'expose plus aucun ancien chemin français de la zone", () => {
    const paths = router.getRoutes().map((route) => route.path);
    for (const old of [
      "/connexion",
      "/inscription",
      "/mot-de-passe-oublie",
      "/reinitialiser-mot-de-passe",
      "/confirmer-email",
      "/cgu",
      "/confidentialite",
      "/invitations/accepter",
      "/salons/nouveau",
    ]) {
      expect(paths).not.toContain(old);
    }
    expect(paths.some((path) => path.startsWith("/salons"))).toBe(false);
  });

  it("donne des titres pt-PT aux pages légales", () => {
    expect(router.getRoutes().find((r) => r.name === "terms")?.meta.title).toContain("Termos");
    expect(router.getRoutes().find((r) => r.name === "privacy")?.meta.title).toContain("Privacidade");
  });
});
