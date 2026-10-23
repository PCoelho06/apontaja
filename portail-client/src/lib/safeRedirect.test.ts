import { describe, expect, it } from "vitest";

import { safeRedirect } from "@/lib/safeRedirect";

describe("safeRedirect", () => {
  it("accepte un chemin interne, avec query", () => {
    expect(safeRedirect("/salons/abc/reserver?service=1&start=2099-06-10T09%3A00%3A00Z")).toBe(
      "/salons/abc/reserver?service=1&start=2099-06-10T09%3A00%3A00Z",
    );
    expect(safeRedirect("/")).toBe("/");
  });

  it("refuse les URL absolues et les chemins protocole-relatifs", () => {
    expect(safeRedirect("https://evil.example")).toBeNull();
    expect(safeRedirect("//evil.example/x")).toBeNull();
    expect(safeRedirect("javascript:alert(1)")).toBeNull();
    expect(safeRedirect("salons/abc")).toBeNull();
  });

  it("refuse les barres obliques inverses et les caractères de contrôle", () => {
    expect(safeRedirect("/\\evil.example")).toBeNull();
    expect(safeRedirect("/ok\nHeader: x")).toBeNull();
  });

  it("refuse tout ce qui n'est pas un texte non vide", () => {
    expect(safeRedirect("")).toBeNull();
    expect(safeRedirect(undefined)).toBeNull();
    expect(safeRedirect(["/a"])).toBeNull();
    expect(safeRedirect(42)).toBeNull();
  });
});
