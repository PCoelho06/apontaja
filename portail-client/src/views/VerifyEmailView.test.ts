import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import VerifyEmailView from "@/views/VerifyEmailView.vue";
import { useVerificationStore } from "@/stores/verification";

const EMPTY = { render: () => null };

function mountView(pendingEmail: string | null) {
  const pinia = createPinia();
  setActivePinia(pinia);
  useVerificationStore().pendingEmail = pendingEmail;
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/connexion", name: "login", component: EMPTY }],
  });
  return mount(VerifyEmailView, { global: { plugins: [pinia, router] } });
}

function sentBody() {
  return JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body));
}

describe("VerifyEmailView", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void path;
        void init;
        return new Response(null, { status: 204 });
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("affiche l'adresse en attente et renvoie l'email avec le portail CLIENT", async () => {
    const wrapper = mountView("alice@example.com");
    expect(wrapper.text()).toContain("alice@example.com");

    const button = wrapper.findAll("button").find((b) => b.text().includes("Renvoyer"));
    await button?.trigger("click");
    await flushPromises();

    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe("/api/auth/resend-verification-email");
    expect(sentBody()).toEqual({ email: "alice@example.com", portal: "CLIENT" });
    wrapper.unmount();
  });

  it("désactive le renvoi pendant le délai d'attente", async () => {
    const wrapper = mountView("alice@example.com");

    const button = wrapper.findAll("button").find((b) => b.text().includes("Renvoyer"));
    await button?.trigger("click");
    await flushPromises();

    const after = wrapper.findAll("button").find((b) => b.text().includes("Renvoyer"));
    expect(after?.attributes("disabled")).toBeDefined();
    expect(after?.text()).toContain("60 s");
    wrapper.unmount();
  });

  it("sans adresse en attente, demande l'email puis renvoie le lien", async () => {
    const wrapper = mountView(null);

    await wrapper.find('input[type="email"]').setValue("bob@example.com");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(sentBody()).toEqual({ email: "bob@example.com", portal: "CLIENT" });
    expect(useVerificationStore().pendingEmail).toBe("bob@example.com");
    wrapper.unmount();
  });
});
