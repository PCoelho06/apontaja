import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";

import { useVerificationStore } from "@/stores/verification";

describe("useVerificationStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("démarre sans email en attente", () => {
    expect(useVerificationStore().pendingEmail).toBeNull();
  });

  it("mémorise puis oublie l'email en attente", () => {
    const store = useVerificationStore();

    store.remember("alice@example.com");
    expect(store.pendingEmail).toBe("alice@example.com");

    store.clear();
    expect(store.pendingEmail).toBeNull();
  });
});
