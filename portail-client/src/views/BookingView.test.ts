import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";

import { useAuthStore } from "@/stores/auth";
import BookingView from "@/views/BookingView.vue";

const EMPTY = { render: () => null };
const START = "2099-06-10T09:00:00Z";

const SALON = {
  salonId: "s1",
  name: "Salão Teste",
  address: "Rua do Teste 1",
  postalCode: "1000-001",
  city: "Lisboa",
  country: "Portugal",
  phone: null,
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
    description: null,
    defaultDurationMinutes: 30,
    defaultPriceCents: 2500,
    resources: [
      { resourceId: "r1", name: "Ana", priceCents: 2500, durationMinutes: 30 },
    ],
  },
];

const SLOTS = {
  serviceId: "sv1",
  timezone: "Europe/Lisbon",
  resources: [],
  days: [
    {
      date: "2099-06-10",
      slots: [{ startAt: START, resourceIds: ["r1"] }],
    },
  ],
};

const BOOKED = {
  appointmentId: "a1",
  salonId: "s1",
  serviceId: "sv1",
  serviceName: "Corte",
  resourceId: "r1",
  resourceName: "Ana",
  startAt: START,
  endAt: "2099-06-10T09:30:00Z",
  status: "SCHEDULED",
  priceCents: 2500,
  durationMinutes: 30,
  createdAt: "2099-01-01T00:00:00Z",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** Réseau simulé par chemin ; une `Response` neuve à chaque appel. */
function stubNetwork(booking: () => Response = () => json(BOOKED, 201)) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    void init;
    if (path === "/api/public/salons/s1") return json(SALON);
    if (path === "/api/public/salons/s1/services") return json(OFFERINGS);
    if (path.startsWith("/api/public/salons/s1/services/sv1/slots")) {
      return json(SLOTS);
    }
    if (path === "/api/me/salons/s1/bookings") return booking();
    return json({}, 404);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function mountView(url: string, signedIn: { verified: boolean } | null) {
  const pinia = createPinia();
  setActivePinia(pinia);
  if (signedIn) {
    const auth = useAuthStore();
    auth.accessToken = "tok";
    auth.account = {
      accountId: "acc1",
      email: "alice@example.com",
      firstName: "Alice",
      lastName: "Martin",
      emailVerified: signedIn.verified,
    };
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/salao/:salonId", name: "salon", component: EMPTY },
      {
        path: "/salao/:salonId/reservar",
        name: "booking",
        component: BookingView,
      },
      { path: "/entrar", name: "login", component: EMPTY },
      { path: "/registar", name: "register", component: EMPTY },
      { path: "/marcacoes", name: "my-appointments", component: EMPTY },
      { path: "/", name: "home", component: EMPTY },
    ],
  });
  await router.push(url);
  await router.isReady();
  const wrapper = mount(BookingView, { global: { plugins: [pinia, router] } });
  return { wrapper, router };
}

function buttonWithText(
  wrapper: Awaited<ReturnType<typeof mountView>>["wrapper"],
  text: string,
) {
  return wrapper.findAll("button").find((button) => button.text() === text);
}

describe("BookingView (portail client)", () => {
  beforeEach(() => {
    stubNetwork();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("affiche les horaires dans le fuseau du salon", async () => {
    const { wrapper } = await mountView(
      "/salao/s1/reservar?service=sv1",
      null,
    );

    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Escolha um horário"),
    );
    await vi.waitFor(() =>
      expect(buttonWithText(wrapper, "10:00")).toBeDefined(),
    );
  });

  it("mène un visiteur à la confirmation puis à la connexion avec retour", async () => {
    const { wrapper, router } = await mountView(
      "/salao/s1/reservar?service=sv1",
      null,
    );
    await vi.waitFor(() =>
      expect(buttonWithText(wrapper, "10:00")).toBeDefined(),
    );

    await buttonWithText(wrapper, "10:00")?.trigger("click");
    await vi.waitFor(() =>
      expect(router.currentRoute.value.query.start).toBe(START),
    );
    expect(wrapper.text()).toContain("Confirme a sua marcação");
    expect(wrapper.text()).toContain("inicie sessão ou crie uma conta");

    await buttonWithText(wrapper, "Iniciar sessão")?.trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.name).toBe("login");
    const redirect = String(router.currentRoute.value.query.redirect);
    expect(redirect.startsWith("/salao/s1/reservar?")).toBe(true);
    expect(redirect).toContain("service=sv1");
    expect(redirect).toContain("start=");
  });

  it("revérifie le créneau de l'URL au retour après connexion", async () => {
    const fetchMock = stubNetwork();
    const { wrapper } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );

    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Confirme a sua marcação"),
    );

    const paths = fetchMock.mock.calls.map((call) => call[0]);
    expect(
      paths.some((path) => path.includes("from=2099-06-10&to=2099-06-10")),
    ).toBe(true);
  });

  it("retire un créneau de l'URL qui n'existe plus", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void init;
        if (path === "/api/public/salons/s1") return json(SALON);
        if (path === "/api/public/salons/s1/services") return json(OFFERINGS);
        return json({ ...SLOTS, days: [] });
      }),
    );
    const { wrapper, router } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain("já não está disponível");
      expect(router.currentRoute.value.query.start).toBeUndefined();
    });
  });

  it("envoie la réservation et affiche « demande envoyée » si le salon confirme à la main", async () => {
    const fetchMock = stubNetwork();
    const { wrapper, router } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Confirme a sua marcação"),
    );

    await wrapper.find('input[type="tel"]').setValue("912345678");
    await wrapper.find('input[type="checkbox"]').setValue(true);
    await wrapper.find("form").trigger("submit");

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain("Pedido de marcação enviado");
      expect(router.currentRoute.value.query.start).toBeUndefined();
    });
    const call = fetchMock.mock.calls.find(
      (candidate) => candidate[0] === "/api/me/salons/s1/bookings",
    );
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      serviceId: "sv1",
      resourceId: null,
      startAt: START,
      phone: "912345678",
      shareContactConsent: true,
    });
  });

  it("affiche « marcação confirmada » quand le back renvoie CONFIRMED", async () => {
    stubNetwork(() => json({ ...BOOKED, status: "CONFIRMED" }, 201));
    const { wrapper } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Confirme a sua marcação"),
    );

    await wrapper.find("form").trigger("submit");

    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Marcação confirmada"),
    );
  });

  it("revient aux horaires quand le créneau vient d'être pris", async () => {
    stubNetwork(() =>
      json({ detail: "Créneau indisponible.", code: "SLOT_UNAVAILABLE" }, 409),
    );
    const { wrapper, router } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Confirme a sua marcação"),
    );

    await wrapper.find("form").trigger("submit");

    await vi.waitFor(() => {
      expect(wrapper.text()).toContain("Escolha um horário");
      expect(router.currentRoute.value.query.start).toBeUndefined();
    });
    expect(wrapper.text()).toContain("já não está disponível");
    expect(wrapper.text()).not.toContain("Créneau indisponible");
  });

  it("affiche le consentement requis en portugais, sans le texte du back", async () => {
    stubNetwork(() =>
      json(
        { detail: "Consentement requis.", code: "SHARING_CONSENT_REQUIRED" },
        400,
      ),
    );
    const { wrapper } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Confirme a sua marcação"),
    );

    await wrapper.find("form").trigger("submit");

    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("autorizar a partilha dos seus dados"),
    );
    expect(wrapper.text()).not.toContain("Consentement requis");
    expect(wrapper.text()).toContain("Confirme a sua marcação");
  });

  it("bloque l'envoi tant que l'email du compte n'est pas vérifié", async () => {
    const { wrapper } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: false },
    );
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Ainda não confirmou"),
    );

    const submit = wrapper.find('button[type="submit"]');
    expect(submit.attributes("disabled")).toBeDefined();
  });

  it("mène à « mes rendez-vous » depuis le résultat de la réservation", async () => {
    const { wrapper, router } = await mountView(
      `/salao/s1/reservar?service=sv1&start=${START}`,
      { verified: true },
    );
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("Confirme a sua marcação"),
    );
    await wrapper.find("form").trigger("submit");
    await vi.waitFor(() =>
      expect(buttonWithText(wrapper, "Ver as minhas marcações")).toBeDefined(),
    );

    await buttonWithText(wrapper, "Ver as minhas marcações")?.trigger("click");

    await vi.waitFor(() =>
      expect(router.currentRoute.value.name).toBe("my-appointments"),
    );
  });

  it("signale un salon sans réservation en ligne", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        void path;
        void init;
        return json({ detail: "Introuvable." }, 404);
      }),
    );
    const { wrapper } = await mountView(
      "/salao/s1/reservar?service=sv1",
      null,
    );

    await vi.waitFor(() =>
      expect(wrapper.text()).toContain("não aceita marcações online"),
    );
  });
});


