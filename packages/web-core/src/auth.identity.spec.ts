import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { defineAuthStore } from "./auth";

const useAuthStore = defineAuthStore({ portal: "SALON" });

const LOGIN = {
  accountId: "a1",
  email: "alice@example.com",
  accessToken: "tok",
};
const ME = {
  accountId: "a1",
  email: "alice@example.com",
  firstName: "Alice",
  lastName: "Martin",
  emailVerified: true,
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/**
 * Simule le réseau par chemin. Chaque appel reçoit une Response neuve : le corps d'une
 * Response ne se lit qu'une fois (réutiliser la même provoque « Body has already been read »).
 */
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

describe("auth store, identité du compte", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("charge le prénom et le nom via /me après la connexion", async () => {
    const fetchMock = stubApi({
      "/api/auth/login": () => jsonResponse(LOGIN),
      "/api/account/me": () => jsonResponse(ME),
    });
    const store = useAuthStore();

    await store.login("alice@example.com", "mot-de-passe-solide");

    expect(store.accessToken).toBe("tok");
    expect(store.account).toEqual({
      accountId: "a1",
      email: "alice@example.com",
      firstName: "Alice",
      lastName: "Martin",
      emailVerified: true,
    });
    const meCall = fetchMock.mock.calls.find(
      ([path]) => path === "/api/account/me",
    );
    expect((meCall?.[1]?.headers as Headers).get("Authorization")).toBe(
      "Bearer tok",
    );
  });

  it("annule la connexion si l'identité ne peut pas être chargée", async () => {
    stubApi({
      "/api/auth/login": () => jsonResponse(LOGIN),
      "/api/account/me": () => jsonResponse({ detail: "boom" }, 500),
    });
    const store = useAuthStore();

    await expect(
      store.login("alice@example.com", "mot-de-passe-solide"),
    ).rejects.toThrow("boom");

    expect(store.accessToken).toBeNull();
    expect(store.account).toBeNull();
  });

  it("envoie identité, consentements et portail à l'inscription", async () => {
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

    const [, init] = fetchMock.mock.calls[0] ?? [];
    expect(JSON.parse(String(init?.body))).toEqual({
      email: "alice@example.com",
      password: "mot-de-passe-solide",
      firstName: "Alice",
      lastName: "Martin",
      acceptTos: true,
      acceptPrivacy: true,
      portal: "SALON",
    });
  });

  it("restaure la session avec l'identité complète", async () => {
    stubApi({
      "/api/auth/refresh": () =>
        jsonResponse({ accountId: "a1", accessToken: "tok2" }),
      "/api/account/me": () => jsonResponse(ME),
    });
    const store = useAuthStore();

    await store.restoreSession();

    expect(store.accessToken).toBe("tok2");
    expect(store.account?.firstName).toBe("Alice");
    expect(store.account?.lastName).toBe("Martin");
  });

  it("efface la session si la restauration échoue", async () => {
    stubApi({
      "/api/auth/refresh": () => jsonResponse({ detail: "expiré" }, 500),
    });
    const store = useAuthStore();

    await store.restoreSession();

    expect(store.accessToken).toBeNull();
    expect(store.account).toBeNull();
  });
});


