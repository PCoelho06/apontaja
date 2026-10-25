import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import type { ClientAppointment } from "@/lib/myAppointments";
import { useAuthStore } from "@/stores/auth";
import MyAppointmentsView from "@/views/MyAppointmentsView.vue";

const EMPTY = { render: () => null };

// jsdom n'implémente pas showModal()/close() : on simule leur effet sur l'attribut `open`.
beforeAll(() => {
  Object.defineProperty(HTMLDialogElement.prototype, "open", {
    configurable: true,
    get(this: HTMLDialogElement) {
      return this.hasAttribute("open");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    writable: true,
    value(this: HTMLDialogElement) {
      this.setAttribute("open", "");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    writable: true,
    value(this: HTMLDialogElement) {
      this.removeAttribute("open");
      this.dispatchEvent(new Event("close"));
    },
  });
});

function appointment(
  id: string,
  overrides: Partial<ClientAppointment> = {},
): ClientAppointment {
  return {
    appointmentId: id,
    salon: { id: "s1", name: "Salão Teste", city: "Lisboa", timezone: "Europe/Lisbon" },
    service: { id: "sv1", name: "Corte" },
    resource: { id: "r1", name: "Ana" },
    startAt: "2099-06-10T09:00:00Z",
    endAt: "2099-06-10T09:30:00Z",
    status: "CONFIRMED",
    priceCents: 2500,
    durationMinutes: 30,
    cancellable: true,
    cancellableUntil: "2099-06-09T09:00:00Z",
    cancelledAt: null,
    ...overrides,
  };
}

function page(content: ClientAppointment[], pageIndex = 0, totalPages = 1) {
  return { content, page: pageIndex, size: 10, totalElements: content.length, totalPages };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

type Handler = (path: string, init?: RequestInit) => Response;

function stubNetwork(handler: Handler) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => handler(path, init));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function mountView() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const auth = useAuthStore();
  auth.accessToken = "tok";
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/marcacoes", name: "my-appointments", component: MyAppointmentsView },
      { path: "/entrar", name: "login", component: EMPTY },
      { path: "/", name: "home", component: EMPTY },
    ],
  });
  await router.push("/marcacoes");
  await router.isReady();
  const wrapper = mount(MyAppointmentsView, { global: { plugins: [pinia, router] } });
  return { wrapper, router };
}

type Wrapper = Awaited<ReturnType<typeof mountView>>["wrapper"];

function buttonWithText(wrapper: Wrapper, text: string) {
  return wrapper.findAll("button").find((button) => button.text() === text);
}

describe("MyAppointmentsView (portail client)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe("liste", () => {
    beforeEach(() => {
      stubNetwork((path) => {
        if (path.includes("scope=past")) {
          return json(
            page([
              appointment("p1", {
                status: "COMPLETED",
                cancellable: false,
                cancellableUntil: null,
                startAt: "2020-01-10T09:00:00Z",
                endAt: "2020-01-10T09:30:00Z",
              }),
            ]),
          );
        }
        return json(page([appointment("a1")]));
      });
    });

    it("affiche les marcações à venir en portugais, dans le fuseau du salon", async () => {
      const { wrapper } = await mountView();

      await vi.waitFor(() => expect(wrapper.text()).toContain("Corte"));
      expect(wrapper.text()).toContain("Salão Teste · Lisboa");
      expect(wrapper.text()).toContain("Confirmada");
      expect(wrapper.text()).toContain("10:00");
      expect(wrapper.text()).toContain("Pode cancelar até");
      expect(buttonWithText(wrapper, "Próximas")?.attributes("aria-pressed")).toBe("true");
    });

    it("passe aux marcações passées", async () => {
      const { wrapper } = await mountView();
      await vi.waitFor(() => expect(wrapper.text()).toContain("Confirmada"));

      await buttonWithText(wrapper, "Passadas")?.trigger("click");

      await vi.waitFor(() => expect(wrapper.text()).toContain("Concluída"));
      expect(buttonWithText(wrapper, "Passadas")?.attributes("aria-pressed")).toBe("true");
      expect(buttonWithText(wrapper, "Cancelar")).toBeUndefined();
    });
  });

  it("indique quand il n'y a rien à afficher", async () => {
    stubNetwork(() => json(page([], 0, 0)));
    const { wrapper } = await mountView();

    await vi.waitFor(() => expect(wrapper.text()).toContain("Não tem marcações próximas."));
  });

  it("ajoute la page suivante avec « Ver mais »", async () => {
    const fetchMock = stubNetwork((path) =>
      path.includes("page=0")
        ? json(page([appointment("a1")], 0, 2))
        : json(page([appointment("a2", { service: { id: "sv2", name: "Coloração" } })], 1, 2)),
    );
    const { wrapper } = await mountView();
    await vi.waitFor(() => expect(buttonWithText(wrapper, "Ver mais")).toBeDefined());

    await buttonWithText(wrapper, "Ver mais")?.trigger("click");

    await vi.waitFor(() => expect(wrapper.text()).toContain("Coloração"));
    expect(wrapper.text()).toContain("Corte");
    expect(buttonWithText(wrapper, "Ver mais")).toBeUndefined();
    expect(fetchMock.mock.calls.some((call) => call[0].includes("page=1"))).toBe(true);
  });

  it("explique que le délai est passé pour un rendez-vous confirmé non annulable", async () => {
    stubNetwork(() => json(page([appointment("a1", { cancellable: false })])));
    const { wrapper } = await mountView();

    await vi.waitFor(() => expect(wrapper.text()).toContain("O prazo de cancelamento terminou"));
    expect(buttonWithText(wrapper, "Cancelar")).toBeUndefined();
  });

  it("signale un échec de chargement et permet de réessayer", async () => {
    let fail = true;
    stubNetwork(() => (fail ? json({ detail: "Erreur." }, 500) : json(page([appointment("a1")]))));
    const { wrapper } = await mountView();
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Não foi possível carregar as suas marcações"),
    );
    expect(wrapper.text()).not.toContain("Erreur.");

    fail = false;
    await buttonWithText(wrapper, "Tentar novamente")?.trigger("click");

    await vi.waitFor(() => expect(wrapper.text()).toContain("Corte"));
  });

  describe("annulation", () => {
    it("confirme, annule et met la ligne à jour en place", async () => {
      const fetchMock = stubNetwork((path, init) =>
        init?.method === "PATCH"
          ? json(appointment("a1", { status: "CANCELLED", cancellable: false, cancelledAt: "2099-06-01T10:00:00Z" }))
          : json(page([appointment("a1")])),
      );
      const { wrapper } = await mountView();
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Cancelar")).toBeDefined());

      await buttonWithText(wrapper, "Cancelar")?.trigger("click");
      await vi.waitFor(() => expect(wrapper.text()).toContain("Quer cancelar a marcação «Corte»"));
      await buttonWithText(wrapper, "Cancelar marcação")?.trigger("click");

      await vi.waitFor(() => expect(wrapper.text()).toContain("Marcação cancelada. Receberá"));
      expect(wrapper.text()).toContain("Cancelada");
      expect(buttonWithText(wrapper, "Cancelar")).toBeUndefined();
      const patch = fetchMock.mock.calls.find((call) => call[1]?.method === "PATCH");
      expect(patch?.[0]).toBe("/api/me/appointments/a1/cancel");
      expect((patch?.[1]?.headers as Headers).get("Authorization")).toBe("Bearer tok");
    });

    it("garde la ligne quand on choisit de maintenir la marcação", async () => {
      const fetchMock = stubNetwork(() => json(page([appointment("a1")])));
      const { wrapper } = await mountView();
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Cancelar")).toBeDefined());

      await buttonWithText(wrapper, "Cancelar")?.trigger("click");
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Manter marcação")).toBeDefined());
      await buttonWithText(wrapper, "Manter marcação")?.trigger("click");
      await flushPromises();

      expect(fetchMock.mock.calls.some((call) => call[1]?.method === "PATCH")).toBe(false);
      expect(wrapper.find("dialog").attributes("open")).toBeUndefined();
    });

    it("traduit le délai dépassé par son code puis recharge la liste", async () => {
      let listCalls = 0;
      stubNetwork((path, init) => {
        if (init?.method === "PATCH") {
          return json({ detail: "Délai dépassé.", code: "CANCELLATION_DEADLINE_PASSED" }, 409);
        }
        listCalls += 1;
        return json(page([appointment("a1", { cancellable: listCalls === 1 })]));
      });
      const { wrapper } = await mountView();
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Cancelar")).toBeDefined());

      await buttonWithText(wrapper, "Cancelar")?.trigger("click");
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Cancelar marcação")).toBeDefined());
      await buttonWithText(wrapper, "Cancelar marcação")?.trigger("click");

      await vi.waitFor(() => {
        expect(wrapper.text()).toContain("O prazo de cancelamento terminou. Contacte o salão.");
        expect(buttonWithText(wrapper, "Cancelar")).toBeUndefined();
      });
      expect(wrapper.text()).not.toContain("Délai dépassé");
      expect(listCalls).toBe(2);
    });

    it("garde la boîte ouverte avec un message pour une erreur inattendue", async () => {
      stubNetwork((path, init) =>
        init?.method === "PATCH" ? json({ detail: "Boom." }, 500) : json(page([appointment("a1")])),
      );
      const { wrapper } = await mountView();
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Cancelar")).toBeDefined());

      await buttonWithText(wrapper, "Cancelar")?.trigger("click");
      await vi.waitFor(() => expect(buttonWithText(wrapper, "Cancelar marcação")).toBeDefined());
      await buttonWithText(wrapper, "Cancelar marcação")?.trigger("click");

      await vi.waitFor(() =>
        expect(wrapper.text()).toContain("Não foi possível cancelar a marcação"),
      );
      expect(wrapper.text()).not.toContain("Boom.");
      expect(wrapper.find("dialog").attributes("open")).toBeDefined();
    });
  });
});


