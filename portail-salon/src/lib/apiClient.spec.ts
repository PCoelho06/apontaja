import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { apiGet, configureAuthClient } from "./apiClient";

describe("apiClient", () => {
  const accessToken = {
    value: "ancien-jwt" as string | null,
  };

  const clearSession = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());

    accessToken.value = "ancien-jwt";
    clearSession.mockReset();

    configureAuthClient({
      getAccessToken: () => accessToken.value,
      setAccessToken: (token) => {
        accessToken.value = token;
      },
      clearSession,
    });

    document.cookie =
      "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("rafraîchit le token après un 401 puis rejoue la requête", async () => {
    document.cookie = "XSRF-TOKEN=le-token-csrf";

    const fetchMock = vi.mocked(fetch);

    fetchMock
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            detail: "Token expiré.",
          }),
          {
            status: 401,
            headers: { "Content-Type": "application/json" },
          },
        ),
      )
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
            value: "ok",
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        ),
      );

    const result = await apiGet<{ value: string }>(
      "/api/test",
      accessToken.value!,
    );

    expect(result.value).toBe("ok");
    expect(accessToken.value).toBe("nouveau-jwt");

    expect(fetchMock).toHaveBeenCalledTimes(3);

    const [, firstRequestOptions] = fetchMock.mock.calls[0];
    const [, refreshRequestOptions] = fetchMock.mock.calls[1];
    const [, retryRequestOptions] = fetchMock.mock.calls[2];

    const firstHeaders = new Headers(firstRequestOptions?.headers);
    const refreshHeaders = new Headers(refreshRequestOptions?.headers);
    const retryHeaders = new Headers(retryRequestOptions?.headers);

    expect(firstHeaders.get("Authorization")).toBe("Bearer ancien-jwt");

    expect(refreshHeaders.get("X-XSRF-TOKEN")).toBe("le-token-csrf");

    expect(retryHeaders.get("Authorization")).toBe("Bearer nouveau-jwt");
  });

  it("deux requêtes simultanées en 401 utilisent un seul refresh", async () => {
    document.cookie = "XSRF-TOKEN=le-token-csrf";

    const fetchMock = vi.mocked(fetch);

    fetchMock.mockImplementation(async (input, options) => {
      const path =
        typeof input === "string"
          ? input
          : input instanceof Request
            ? input.url
            : input.toString();

      if (path === "/api/auth/refresh") {
        return new Response(
          JSON.stringify({
            accountId: "1",
            accessToken: "nouveau-jwt",
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      const headers = new Headers(options?.headers);
      const authorization = headers.get("Authorization");

      if (authorization === "Bearer ancien-jwt") {
        return new Response(
          JSON.stringify({
            detail: "Token expiré.",
          }),
          {
            status: 401,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      if (authorization === "Bearer nouveau-jwt") {
        return new Response(
          JSON.stringify({
            value: "ok",
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        );
      }

      throw new Error(`Requête inattendue : ${path}`);
    });

    const [firstResult, secondResult] = await Promise.all([
      apiGet<{ value: string }>("/api/test/1", accessToken.value!),
      apiGet<{ value: string }>("/api/test/2", accessToken.value!),
    ]);

    expect(firstResult.value).toBe("ok");
    expect(secondResult.value).toBe("ok");
    expect(accessToken.value).toBe("nouveau-jwt");

    const refreshCalls = fetchMock.mock.calls.filter(([input]) => {
      const path =
        typeof input === "string"
          ? input
          : input instanceof Request
            ? input.url
            : input.toString();

      return path === "/api/auth/refresh";
    });

    expect(refreshCalls).toHaveLength(1);

    const retryCalls = fetchMock.mock.calls.filter(([input, options]) => {
      const path =
        typeof input === "string"
          ? input
          : input instanceof Request
            ? input.url
            : input.toString();

      const authorization = new Headers(options?.headers).get("Authorization");

      return (
        (path === "/api/test/1" || path === "/api/test/2") &&
        authorization === "Bearer nouveau-jwt"
      );
    });

    expect(retryCalls).toHaveLength(2);

    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  it("échec du refresh vide la session et retourne une erreur", async () => {
    document.cookie = "XSRF-TOKEN=le-token-csrf";

    const fetchMock = vi.mocked(fetch);

    fetchMock
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            detail: "Token expiré.",
          }),
          {
            status: 401,
            headers: { "Content-Type": "application/json" },
          },
        ),
      )
      .mockResolvedValueOnce(
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

    await expect(apiGet("/api/test", accessToken.value!)).rejects.toMatchObject(
      {
        status: 401,
        message: "Token expiré.",
      },
    );

    expect(accessToken.value).toBe("ancien-jwt");
    expect(clearSession).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("ne tente pas de refresh une requête sans access token", async () => {
    const fetchMock = vi.mocked(fetch);

    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          detail: "Non authentifié.",
        }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    await expect(
      apiGet("/api/public-or-unauthenticated"),
    ).rejects.toMatchObject({
      status: 401,
      message: "Non authentifié.",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [input] = fetchMock.mock.calls[0];

    expect(input).toBe("/api/public-or-unauthenticated");
  });
});
