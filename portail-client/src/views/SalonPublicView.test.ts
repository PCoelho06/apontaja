import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import SalonPublicView from "@/views/SalonPublicView.vue";

const EMPTY = { render: () => null };

const SALON = {
  salonId: "s1",
  name: "Salão Teste",
  address: "Rua do Teste 1",
  postalCode: "1000-001",
  city: "Lisboa",
  country: "Portugal",
  phone: "210000000",
  timezone: "Europe/Lisbon",
  autoConfirm: false,
  minNoticeMinutes: 120,
  horizonDays: 60,
  cancellationDeadlineHours: 24,
};

const OFFERINGS = [
  {
    serviceId: "sv1",
    name: "Corte",
    description: "Corte e lavagem",
    defaultDurationMinutes: 30,
    defaultPriceCents: 2500,
    resources: [
      { resourceId: "r1", name: "Ana", priceCents: 2500, durationMinutes: 30 },
      { resourceId: "r2", name: "Beatriz", priceCents: 3000, durationMinutes: 45 },
    ],
  },
];

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

async function mountView() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/salons/:salonId", name: "salon", component: SalonPublicView },
      { path: "/salons/:salonId/reserver", name: "booking", component: EMPTY },
      { path: "/connexion", name: "login", component: EMPTY },
      { path: "/", name: "home", component: EMPTY },
    ],
  });
  await router.push("/salons/s1");
  await router.isReady();
  const wrapper = mount(SalonPublicView, { global: { plugins: [pinia, router] } });
  return { wrapper, router };
}

describe("SalonPublicView (portail client)", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void init;
        if (path === "/api/public/salons/s1") return json(SALON);
        if (path === "/api/public/salons/s1/services") return json(OFFERINGS);
        return json({}, 404);
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("présente le salon et ses prestations en portugais", async () => {
    const { wrapper } = await mountView();

    await vi.waitFor(() => expect(wrapper.text()).toContain("Salão Teste"));
    expect(wrapper.text()).toContain("Rua do Teste 1, 1000-001 Lisboa");
    expect(wrapper.text()).toContain("Corte");
    expect(wrapper.text()).toContain("de 30 min a 45 min");
    expect(wrapper.text()).toContain("O salão confirma cada pedido");
    expect(wrapper.text()).toContain("até 24 h antes");
  });

  it("ouvre la réservation de la prestation choisie", async () => {
    const { wrapper, router } = await mountView();
    await vi.waitFor(() => expect(wrapper.text()).toContain("Corte"));

    await wrapper
      .findAll("button")
      .find((button) => button.text() === "Marcar")
      ?.trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.name).toBe("booking");
    expect(router.currentRoute.value.params.salonId).toBe("s1");
    expect(router.currentRoute.value.query.service).toBe("sv1");
  });

  it("signale un salon introuvable ou sans réservation en ligne", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void path;
        void init;
        return json({ detail: "Introuvable." }, 404);
      }),
    );
    const { wrapper } = await mountView();

    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("não foi encontrado"),
    );
  });
});
