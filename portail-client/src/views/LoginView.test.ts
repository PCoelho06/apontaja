import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import LoginView from "@/views/LoginView.vue";

const EMPTY = { render: () => null };

async function mountView() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", name: "home", component: EMPTY },
      { path: "/entrar", name: "login", component: EMPTY },
      { path: "/registar", name: "register", component: EMPTY },
      {
        path: "/recuperar-palavra-passe",
        name: "forgot-password",
        component: EMPTY,
      },
    ],
  });
  await router.push("/entrar");
  await router.isReady();
  const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } });
  return { wrapper, router };
}

describe("LoginView (portail client)", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void path;
        void init;
        return new Response(
          JSON.stringify({ detail: "Identifiants invalides." }),
          {
            status: 401,
            headers: { "content-type": "application/json" },
          },
        );
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("affiche l'interface en portugais", async () => {
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain("Entrar");
    expect(wrapper.text()).toContain("Esqueceu-se da palavra-passe?");
    expect(wrapper.text()).toContain("Criar conta");
  });

  it("traduit un refus d'identifiants (401) sans afficher le message du back", async () => {
    const { wrapper, router } = await mountView();
    await wrapper.find('input[autocomplete="email"]').setValue("a@example.com");
    await wrapper
      .find('input[autocomplete="current-password"]')
      .setValue("mauvais-mot-de-passe");

    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("Email ou palavra-passe incorretos.");
    expect(wrapper.text()).not.toContain("Identifiants invalides.");
    expect(router.currentRoute.value.name).toBe("login");
  });
});
