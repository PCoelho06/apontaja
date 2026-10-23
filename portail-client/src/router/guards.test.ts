import { describe, expect, it } from "vitest";

import { resolveNavigation } from "@/router/guards";

describe("resolveNavigation", () => {
  it("renvoie un visiteur vers la connexion sur une page protégée", () => {
    expect(resolveNavigation({ requiresAuth: true }, false)).toEqual({
      name: "login",
    });
  });

  it("laisse passer un compte connecté sur une page protégée", () => {
    expect(resolveNavigation({ requiresAuth: true }, true)).toBe(true);
  });

  it("renvoie un compte connecté vers l'accueil sur une page de visiteur", () => {
    expect(resolveNavigation({ guestOnly: true }, true)).toEqual({
      name: "home",
    });
  });

  it("laisse passer un visiteur sur une page réservée aux visiteurs", () => {
    expect(resolveNavigation({ guestOnly: true }, false)).toBe(true);
  });

  it("laisse passer les pages publiques dans tous les cas", () => {
    expect(resolveNavigation({}, false)).toBe(true);
    expect(resolveNavigation({}, true)).toBe(true);
  });

  it("mémorise la page demandée quand une page protégée exige la connexion", () => {
    expect(
      resolveNavigation({ requiresAuth: true }, false, {
        fullPath: "/salons/abc/reserver?service=1",
      }),
    ).toEqual({
      name: "login",
      query: { redirect: "/salons/abc/reserver?service=1" },
    });
  });

  it("ne mémorise pas l'accueil ni une destination non sûre", () => {
    expect(
      resolveNavigation({ requiresAuth: true }, false, { fullPath: "/" }),
    ).toEqual({ name: "login" });
    expect(
      resolveNavigation({ requiresAuth: true }, false, {
        fullPath: "//evil.example",
      }),
    ).toEqual({ name: "login" });
  });

  it("renvoie un compte connecté vers la destination sûre demandée", () => {
    expect(
      resolveNavigation({ guestOnly: true }, true, {
        redirect: "/salons/abc/reserver?service=1",
      }),
    ).toEqual({ path: "/salons/abc/reserver?service=1" });
  });

  it("ignore une destination non sûre sur une page de visiteur", () => {
    expect(
      resolveNavigation({ guestOnly: true }, true, {
        redirect: "https://evil.example",
      }),
    ).toEqual({ name: "home" });
  });
});
