import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/stores/auth";

function stubApi(path: string) {
  const fetchMock = vi.fn(async (requested: string, init?: RequestInit) => {
    void init;
    if (requested !== path) {
      throw new Error(`appel inattendu : ${requested}`);
    }
    return new Response(null, { status: 204 });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function sentBody(fetchMock: ReturnType<typeof stubApi>) {
  const [, init] = fetchMock.mock.calls[0] ?? [];
  return JSON.parse(String(init?.body));
}

describe("useAuthStore (portail salon)", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("déclare le portail SALON à l'inscription", async () => {
    const fetchMock = stubApi("/api/auth/register");

    await useAuthStore().register({
      email: "alice@example.com",
      password: "mot-de-passe-solide",
      firstName: "Alice",
      lastName: "Martin",
      acceptTerms: true,
    });

    expect(sentBody(fetchMock)).toMatchObject({
      portal: "SALON",
      acceptTos: true,
      acceptPrivacy: true,
    });
  });

  it("déclare le portail SALON au renvoi et au mot de passe oublié", async () => {
    const resend = stubApi("/api/auth/resend-verification-email");
    await useAuthStore().resendVerificationEmail("alice@example.com");
    expect(sentBody(resend).portal).toBe("SALON");

    const forgot = stubApi("/api/auth/forgot-password");
    await useAuthStore().requestPasswordReset("alice@example.com");
    expect(sentBody(forgot).portal).toBe("SALON");
  });
});
