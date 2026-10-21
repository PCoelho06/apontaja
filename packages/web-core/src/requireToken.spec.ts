import { beforeEach, describe, expect, it } from "vitest";

import { configureAuthClient } from "./apiClient";
import { requireToken } from "./requireToken";

describe("requireToken", () => {
  const session = { token: null as string | null };

  beforeEach(() => {
    session.token = null;
    configureAuthClient({
      getAccessToken: () => session.token,
      setAccessToken: (accessToken) => {
        session.token = accessToken;
      },
      clearSession: () => {
        session.token = null;
      },
    });
  });

  it("renvoie le jeton courant du client configuré", () => {
    session.token = "jwt";

    expect(requireToken()).toBe("jwt");
  });

  it("suit le jeton quand il change", () => {
    session.token = "premier";
    expect(requireToken()).toBe("premier");

    session.token = "second";
    expect(requireToken()).toBe("second");
  });

  it("lève « Non authentifié. » sans jeton", () => {
    expect(() => requireToken()).toThrow("Non authentifié.");
  });
});
