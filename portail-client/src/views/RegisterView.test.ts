import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import RegisterView from "@/views/RegisterView.vue";
import { useVerificationStore } from "@/stores/verification";

const EMPTY = { render: () => null };

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/inscription", name: "register", component: EMPTY },
      { path: "/connexion", name: "login", component: EMPTY },
      { path: "/verifiez-votre-email", name: "verify-email", component: EMPTY },
      { path: "/cgu", name: "terms", component: EMPTY },
      { path: "/confidentialite", name: "privacy", component: EMPTY },
    ],
  });
}

async function mountView() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = buildRouter();
  await router.push("/inscription");
  await router.isReady();
  const wrapper = mount(RegisterView, { global: { plugins: [pinia, router] } });
  return { wrapper, router };
}

type Wrapper = Awaited<ReturnType<typeof mountView>>["wrapper"];

async function fillForm(wrapper: Wrapper) {
  await wrapper.find('input[autocomplete="given-name"]').setValue("Alice");
  await wrapper.find('input[autocomplete="family-name"]').setValue("Martin");
  await wrapper
    .find('input[autocomplete="email"]')
    .setValue("alice@example.com");
  await wrapper
    .find('input[autocomplete="new-password"]')
    .setValue("mot-de-passe-solide");
}

describe("RegisterView (portail client)", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void path;
        void init;
        return new Response(null, { status: 201 });
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("refuse l'envoi tant que la case CGU n'est pas cochée", async () => {
    const { wrapper } = await mountView();
    await fillForm(wrapper);

    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(fetch).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("Vous devez accepter les CGU");
  });

  it("inscrit avec le portail CLIENT puis ouvre « vérifiez votre email »", async () => {
    const { wrapper, router } = await mountView();
    await fillForm(wrapper);
    await wrapper.find('input[type="checkbox"]').setValue(true);

    await wrapper.find("form").trigger("submit");
    await flushPromises();

    const calls = vi.mocked(fetch).mock.calls;
    expect(calls).toHaveLength(1);
    expect(calls[0]?.[0]).toBe("/api/auth/register");
    expect(JSON.parse(String(calls[0]?.[1]?.body))).toEqual({
      email: "alice@example.com",
      password: "mot-de-passe-solide",
      firstName: "Alice",
      lastName: "Martin",
      acceptTos: true,
      acceptPrivacy: true,
      portal: "CLIENT",
    });
    expect(useVerificationStore().pendingEmail).toBe("alice@example.com");
    expect(router.currentRoute.value.name).toBe("verify-email");
  });

  it("affiche l'erreur du back et reste sur la page", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void path;
        void init;
        return new Response(
          JSON.stringify({ detail: "Adresse déjà utilisée." }),
          {
            status: 409,
            headers: { "content-type": "application/json" },
          },
        );
      }),
    );
    const { wrapper, router } = await mountView();
    await fillForm(wrapper);
    await wrapper.find('input[type="checkbox"]').setValue(true);

    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("Adresse déjà utilisée.");
    expect(router.currentRoute.value.name).toBe("register");
  });
});
