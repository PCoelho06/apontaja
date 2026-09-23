import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

import { useAuthStore } from "./auth";

describe("useAuthStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.stubGlobal("fetch", vi.fn());
    document.cookie =
      "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("login réussi peuple accessToken et account", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          accountId: "1",
          email: "a@example.com",
          accessToken: "jwt",
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const store = useAuthStore();

    await store.login("a@example.com", "motdepassesuffisant");

    expect(store.isAuthenticated).toBe(true);
    expect(store.accessToken).toBe("jwt");
    expect(store.account).toEqual({
      accountId: "1",
      email: "a@example.com",
    });
  });

  it("login en échec ne peuple rien et propage le message d'erreur du backend", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          detail: "Email ou mot de passe incorrect.",
        }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const store = useAuthStore();

    await expect(store.login("a@example.com", "mauvais")).rejects.toThrow(
      "Email ou mot de passe incorrect.",
    );

    expect(store.isAuthenticated).toBe(false);
    expect(store.accessToken).toBeNull();
    expect(store.account).toBeNull();
  });

  it("logout envoie le header X-XSRF-TOKEN et vide le state même si la requête échoue", async () => {
    document.cookie = "XSRF-TOKEN=le-token-csrf";

    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 500 }));

    const store = useAuthStore();

    store.$patch({
      accessToken: "jwt",
      account: {
        accountId: "1",
        email: "a@example.com",
      },
    });

    await expect(store.logout()).rejects.toThrow();

    expect(store.isAuthenticated).toBe(false);
    expect(store.accessToken).toBeNull();
    expect(store.account).toBeNull();

    const [, options] = vi.mocked(fetch).mock.calls[0];

    const headers = new Headers(options?.headers);

    expect(headers.get("X-XSRF-TOKEN")).toBe("le-token-csrf");
  });

  it("restoreSession restaure le token puis récupère le compte courant", async () => {
    document.cookie = "XSRF-TOKEN=le-token-csrf";

    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accountId: "1",
            accessToken: "nouveau-jwt",
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accountId: "1",
            email: "a@example.com",
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        ),
      );

    const store = useAuthStore();

    await store.restoreSession();

    expect(store.isAuthenticated).toBe(true);
    expect(store.accessToken).toBe("nouveau-jwt");
    expect(store.account).toEqual({
      accountId: "1",
      email: "a@example.com",
    });

    expect(fetch).toHaveBeenCalledTimes(2);

    const [, refreshOptions] = vi.mocked(fetch).mock.calls[0];
    const [, meOptions] = vi.mocked(fetch).mock.calls[1];

    const refreshHeaders = new Headers(refreshOptions?.headers);
    const meHeaders = new Headers(meOptions?.headers);

    expect(refreshHeaders.get("X-XSRF-TOKEN")).toBe("le-token-csrf");
    expect(meHeaders.get("Authorization")).toBe("Bearer nouveau-jwt");
  });

  it("restoreSession vide la session si le refresh échoue", async () => {
    document.cookie = "XSRF-TOKEN=le-token-csrf";

    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          detail: "Refresh token invalide.",
        }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const store = useAuthStore();

    store.$patch({
      accessToken: "ancien-jwt",
      account: {
        accountId: "1",
        email: "a@example.com",
      },
    });

    await store.restoreSession();

    expect(store.isAuthenticated).toBe(false);
    expect(store.accessToken).toBeNull();
    expect(store.account).toBeNull();
  });
});
