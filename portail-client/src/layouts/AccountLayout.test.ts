import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { describe, expect, it } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import AccountLayout from "@/layouts/AccountLayout.vue";
import { useAuthStore } from "@/stores/auth";

const EMPTY = { render: () => null };

async function mountLayout(signedIn: boolean) {
  const pinia = createPinia();
  setActivePinia(pinia);
  if (signedIn) {
    useAuthStore().accessToken = "tok";
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", name: "home", component: EMPTY },
      { path: "/mes-rendez-vous", name: "my-appointments", component: EMPTY },
    ],
  });
  await router.push("/");
  await router.isReady();
  const wrapper = mount(AccountLayout, {
    global: { plugins: [pinia, router] },
    slots: { default: "<p>Contenu</p>", actions: "<button>Action</button>" },
  });
  return { wrapper, router };
}

function linkButton(wrapper: Awaited<ReturnType<typeof mountLayout>>["wrapper"]) {
  return wrapper.findAll("button").find((b) => b.text() === "As minhas marcações");
}

describe("AccountLayout (portail client)", () => {
  it("propose « as minhas marcações » à un compte connecté", async () => {
    const { wrapper, router } = await mountLayout(true);

    expect(wrapper.text()).toContain("Contenu");
    expect(wrapper.text()).toContain("Action");
    await linkButton(wrapper)?.trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.name).toBe("my-appointments");
  });

  it("ne l'affiche pas à un visiteur", async () => {
    const { wrapper } = await mountLayout(false);

    expect(linkButton(wrapper)).toBeUndefined();
  });
});
