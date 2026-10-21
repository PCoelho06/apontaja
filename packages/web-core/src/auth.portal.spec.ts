import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { defineAuthStore, type AuthPortal } from "./auth";
import { requireToken } from "./requireToken";

const ME = {
  accountId: "a1",
  email: "alice@example.com",
  firstName: "Alice",
  lastName: "Martin",
  emailVerified: false,
};

/** Simule le réseau : chaque appel reçoit une Response neuve (un corps ne se lit qu'une fois). */
function stubApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    void init;
    const route = routes[path];
    if (!route) {
      throw new Error(`appel inattendu : ${path}`);
    }
    return route();
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function bodyOf(fetchMock: ReturnType<typeof stubApi>, index = 0) {
  const [, init] = fetchMock.mock.calls[index] ?? [];
  return JSON.parse(String(init?.body));
}

describe.each<AuthPortal>(["SALON", "CLIENT"])("defineAuthStore (%s)", (portal) => {
  const useAuthStore = defineAuthStore({ portal });

  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("envoie le portail au renvoi de l'email de vérification", async () => {
    const fetchMock = stubApi({
      "/api/auth/resend-verification-email": () => new Response(null, { status: 204 }),
    });

    await useAuthStore().resendVerificationEmail("alice@example.com");

    expect(bodyOf(fetchMock)).toEqual({ email: "alice@example.com", portal });
  });

  it("envoie le portail au mot de passe oublié", async () => {
    const fetchMock = stubApi({
      "/api/auth/forgot-password": () => new Response(null, { status: 204 }),
    });

    await useAuthStore().requestPasswordReset("alice@example.com");

    expect(bodyOf(fetchMock)).toEqual({ email: "alice@example.com", portal });
  });

  it("n'envoie pas le portail à la confirmation ni à la réinitialisation", async () => {
    const fetchMock = stubApi({
      "/api/auth/confirm-email": () => new Response(null, { status: 204 }),
      "/api/auth/reset-password": () => new Response(null, { status: 204 }),
    });
    const store = useAuthStore();

    await store.confirmEmail("tok-1");
    await store.resetPassword("tok-2", "nouveau-mot-de-passe");

    expect(bodyOf(fetchMock, 0)).toEqual({ token: "tok-1" });
    expect(bodyOf(fetchMock, 1)).toEqual({
      token: "tok-2",
      newPassword: "nouveau-mot-de-passe",
    });
  });

  it("transmet la case unique aux deux consentements et le portail", async () => {
    const fetchMock = stubApi({
      "/api/auth/register": () => new Response(null, { status: 201 }),
    });

    await useAuthStore().register({
      email: "alice@example.com",
      password: "mot-de-passe-solide",
      firstName: "Alice",
      lastName: "Martin",
      acceptTerms: true,
    });

    const body = bodyOf(fetchMock);
    expect(body.portal).toBe(portal);
    expect(body.acceptTos).toBe(true);
    expect(body.acceptPrivacy).toBe(true);
    expect(body).not.toHaveProperty("acceptTerms");
  });

  it("expose l'état de vérification de l'email et alimente requireToken", async () => {
    stubApi({
      "/api/auth/login": () =>
        new Response(
          JSON.stringify({ accountId: "a1", email: ME.email, accessToken: "tok" }),
          { status: 200, headers: { "content-type": "application/json" } },
        ),
      "/api/account/me": () =>
        new Response(JSON.stringify(ME), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
    });
    const store = useAuthStore();

    await store.login(ME.email, "mot-de-passe-solide");

    expect(store.account?.emailVerified).toBe(false);
    expect(requireToken()).toBe("tok");

    store.accessToken = null;
    expect(() => requireToken()).toThrow("Non authentifié.");
  });
});
