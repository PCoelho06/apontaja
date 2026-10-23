<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import SessionActions from "@/components/SessionActions.vue";
import AccountLayout from "@/layouts/AccountLayout.vue";
import { describeBookingError, describeLoadError } from "@/lib/bookingErrors";
import {
  formatDayLabel,
  formatDayMonth,
  formatDuration,
  formatDurationRange,
  formatLongDateTime,
  formatPrice,
  formatPriceRange,
  formatTime,
} from "@/lib/format";
import type {
  OnlineBookingResult,
  PublicDay,
  PublicResource,
  PublicSlot,
} from "@/lib/publicBooking";
import {
  cancellationNote,
  findSlot,
  queryValue,
  resourcesForSlot,
  sameInstant,
  summarizeTerms,
} from "@/lib/publicBooking";
import {
  browserZone,
  dateInZone,
  differsFromBrowserZone,
  weekWindow,
} from "@/lib/salonCalendar";
import { useAuthStore } from "@/stores/auth";
import { useBookingStore } from "@/stores/booking";
import { ApiError } from "@apontaja/web-core/api-client";

import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

type Phase = "loading" | "ready" | "unavailable" | "error";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const booking = useBookingStore();

const phase = ref<Phase>("loading");
const loadError = ref("");

const salonId = computed(() => String(route.params.salonId));
const serviceQuery = computed(() => queryValue(route.query.service));
const resourceQuery = computed(() => queryValue(route.query.resource));
const startQuery = computed(() => queryValue(route.query.start));

const salon = computed(() => booking.salon);
const zone = computed(() => salon.value?.timezone ?? "UTC");
const offering = computed(
  () =>
    booking.offerings.find((item) => item.serviceId === serviceQuery.value) ??
    null,
);
const resources = computed(() => offering.value?.resources ?? []);
const chosenResource = computed<PublicResource | null>(
  () =>
    resources.value.find((item) => item.resourceId === resourceQuery.value) ??
    null,
);
const effectiveResourceId = computed(
  () => chosenResource.value?.resourceId ?? null,
);

// --- Créneaux -------------------------------------------------------------------------------------

const weekIndex = ref(0);
const days = ref<PublicDay[]>([]);
const slotsLoading = ref(false);
const slotsError = ref("");
const notice = ref("");
let loadToken = 0;

const week = computed(() => {
  const current = salon.value;
  if (!current) {
    return null;
  }
  const today = dateInZone(new Date(), current.timezone);
  return weekWindow(today, current.horizonDays, weekIndex.value);
});

const weekLabel = computed(() =>
  week.value
    ? `${formatDayMonth(week.value.from)} – ${formatDayMonth(week.value.to)}`
    : "",
);

const showZoneNote = computed(
  () =>
    salon.value !== null &&
    differsFromBrowserZone(salon.value.timezone, browserZone()),
);

const resourceModel = computed({
  get: () => resourceQuery.value ?? "",
  set: (value: string) => {
    const query: Record<string, string> = {};
    if (serviceQuery.value) {
      query.service = serviceQuery.value;
    }
    if (value) {
      query.resource = value;
    }
    void router.replace({ query });
  },
});

function queryFor(start: string | null): Record<string, string> {
  const query: Record<string, string> = {};
  if (serviceQuery.value) {
    query.service = serviceQuery.value;
  }
  if (resourceQuery.value) {
    query.resource = resourceQuery.value;
  }
  if (start) {
    query.start = start;
  }
  return query;
}

async function loadWeek() {
  const current = week.value;
  const service = offering.value;
  if (!current || !service) {
    return;
  }
  loadToken += 1;
  const token = loadToken;
  slotsLoading.value = true;
  slotsError.value = "";
  try {
    const result = await booking.fetchSlots(
      salonId.value,
      service.serviceId,
      effectiveResourceId.value,
      current.from,
      current.to,
    );
    if (token === loadToken) {
      days.value = result.days;
    }
  } catch (error) {
    if (token === loadToken) {
      days.value = [];
      slotsError.value = describeLoadError(error);
    }
  } finally {
    if (token === loadToken) {
      slotsLoading.value = false;
    }
  }
}

function goToWeek(delta: number) {
  weekIndex.value += delta;
  void loadWeek();
}

// --- Sélection et confirmation --------------------------------------------------------------------

const selectedSlot = ref<PublicSlot | null>(null);
const result = ref<OnlineBookingResult | null>(null);
const phone = ref("");
const consent = ref(false);
const isSubmitting = ref(false);
const submitError = ref("");
const forceUnverified = ref(false);

const step = computed(() => {
  if (result.value) {
    return "result";
  }
  return selectedSlot.value ? "confirm" : "slots";
});

const candidates = computed<PublicResource[]>(() => {
  if (chosenResource.value) {
    return [chosenResource.value];
  }
  return selectedSlot.value
    ? resourcesForSlot(resources.value, selectedSlot.value)
    : resources.value;
});

const terms = computed(() => summarizeTerms(candidates.value));

const isUnverified = computed(
  () =>
    forceUnverified.value ||
    (auth.isAuthenticated && auth.account?.emailVerified === false),
);

const consentLabel = computed(
  () =>
    `Autorizo o salão ${salon.value?.name ?? ""} a guardar o meu nome, email e telemóvel ` +
    "para gerir as minhas marcações.",
);

const isConfirmed = computed(() => result.value?.status === "CONFIRMED");

async function dropStart() {
  await router.replace({ query: queryFor(null) });
}

/** Revérifie, sur le serveur, qu'un créneau demandé par l'URL existe encore (retour après connexion). */
async function restoreSelection() {
  const start = startQuery.value;
  const service = offering.value;
  if (!start || !service) {
    selectedSlot.value = null;
    return;
  }
  const instant = new Date(start);
  if (Number.isNaN(instant.getTime())) {
    await dropStart();
    return;
  }
  const date = dateInZone(instant, zone.value);
  try {
    const slots = await booking.fetchSlots(
      salonId.value,
      service.serviceId,
      effectiveResourceId.value,
      date,
      date,
    );
    const slot = findSlot(slots.days, start);
    if (slot) {
      selectedSlot.value = slot;
      return;
    }
    notice.value = "O horário escolhido já não está disponível. Escolha outro.";
  } catch (error) {
    notice.value = describeLoadError(error);
  }
  await dropStart();
}

async function chooseSlot(slot: PublicSlot) {
  notice.value = "";
  submitError.value = "";
  selectedSlot.value = slot;
  await router.push({ query: queryFor(slot.startAt) });
}

async function backToSlots() {
  await dropStart();
}

function goToLogin() {
  void router.push({ name: "login", query: { redirect: route.fullPath } });
}

function goToRegister() {
  void router.push({ name: "register" });
}

function goToAccount() {
  void router.push({ name: "home" });
}

function goToSalon() {
  void router.push({ name: "salon", params: { salonId: salonId.value } });
}

async function refreshAccount() {
  await auth.loadAccount().catch(() => undefined);
  if (auth.account?.emailVerified) {
    forceUnverified.value = false;
  }
}

async function handleSubmit() {
  const slot = selectedSlot.value;
  const service = offering.value;
  if (!slot || !service || isUnverified.value) {
    return;
  }
  submitError.value = "";
  isSubmitting.value = true;
  try {
    result.value = await booking.book(salonId.value, {
      serviceId: service.serviceId,
      resourceId: effectiveResourceId.value,
      startAt: slot.startAt,
      phone: phone.value.trim() || undefined,
      shareContactConsent: consent.value,
    });
    selectedSlot.value = null;
    await dropStart();
  } catch (error) {
    const failure = describeBookingError(error);
    if (failure.kind === "SLOT_UNAVAILABLE") {
      notice.value = failure.message;
      selectedSlot.value = null;
      booking.clearSlots();
      await dropStart();
    } else if (failure.kind === "UNAUTHENTICATED") {
      goToLogin();
    } else {
      if (failure.kind === "EMAIL_NOT_VERIFIED") {
        forceUnverified.value = true;
      }
      submitError.value = failure.message;
    }
  } finally {
    isSubmitting.value = false;
  }
}

watch(startQuery, (start) => {
  if (start === null) {
    selectedSlot.value = null;
    if (!result.value) {
      void loadWeek();
    }
    return;
  }
  const current = selectedSlot.value;
  if (current && sameInstant(current.startAt, start)) {
    return;
  }
  void restoreSelection();
});

watch(resourceQuery, () => {
  if (phase.value === "ready" && !selectedSlot.value) {
    void loadWeek();
  }
});

onMounted(async () => {
  try {
    await booking.loadSalon(salonId.value);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      phase.value = "unavailable";
    } else {
      loadError.value = describeLoadError(error);
      phase.value = "error";
    }
    return;
  }
  phase.value = "ready";
  if (!offering.value) {
    return;
  }
  if (startQuery.value) {
    // Créneau restauré : la semaine sera chargée au retour. Créneau disparu : `dropStart()` change
    // l'URL et le `watch` de `startQuery` charge la semaine.
    await restoreSelection();
    return;
  }
  await loadWeek();
});
</script>

<template>
  <AccountLayout>
    <template #actions>
      <SessionActions />
    </template>

    <p
      v-if="phase === 'loading'"
      class="text-sm text-ink/70"
    >
      A carregar…
    </p>

    <UiAlert
      v-else-if="phase === 'unavailable'"
      variant="info"
    >
      Este salão não foi encontrado ou não aceita marcações online neste momento.
    </UiAlert>

    <UiAlert
      v-else-if="phase === 'error'"
      variant="error"
    >
      {{ loadError }}
    </UiAlert>

    <template v-else-if="salon && !offering">
      <UiAlert variant="info">
        Este serviço não está disponível para marcação online.
      </UiAlert>
      <div class="mt-4">
        <UiButton
          variant="secondary"
          @click="goToSalon"
        >
          Ver os serviços do salão
        </UiButton>
      </div>
    </template>

    <template v-else-if="salon && offering">
      <RouterLink
        :to="{ name: 'salon', params: { salonId } }"
        class="text-sm text-wine hover:underline"
      >
        ← {{ salon.name }}
      </RouterLink>
      <h1 class="mt-2 font-display text-2xl text-ink">
        {{ offering.name }}
      </h1>

      <UiAlert
        v-if="notice"
        class="mt-4"
        variant="info"
      >
        {{ notice }}
      </UiAlert>

      <!-- Étape 1 : choisir un horaire -->
      <section
        v-if="step === 'slots'"
        class="mt-6 space-y-4"
      >
        <UiSelect
          v-if="resources.length > 1"
          v-model="resourceModel"
          label="Profissional"
        >
          <option value="">
            Sem preferência
          </option>
          <option
            v-for="resource in resources"
            :key="resource.resourceId"
            :value="resource.resourceId"
          >
            {{ resource.name }}
          </option>
        </UiSelect>

        <h2 class="font-display text-lg text-ink">
          Escolha um horário
        </h2>

        <div
          v-if="week"
          class="flex items-center justify-between gap-2"
        >
          <UiButton
            variant="secondary"
            size="sm"
            :disabled="week.isFirst || slotsLoading"
            @click="goToWeek(-1)"
          >
            Semana anterior
          </UiButton>
          <p class="text-sm text-ink/70">
            {{ weekLabel }}
          </p>
          <UiButton
            variant="secondary"
            size="sm"
            :disabled="week.isLast || slotsLoading"
            @click="goToWeek(1)"
          >
            Semana seguinte
          </UiButton>
        </div>

        <p
          v-if="showZoneNote"
          class="text-xs text-ink/60"
        >
          Horários no fuso horário do salão ({{ salon.timezone }}).
        </p>

        <UiAlert
          v-if="slotsError"
          variant="error"
        >
          {{ slotsError }}
        </UiAlert>
        <p
          v-else-if="slotsLoading"
          class="text-sm text-ink/70"
        >
          A carregar horários…
        </p>
        <p
          v-else-if="days.length === 0"
          class="text-sm text-ink/70"
        >
          Sem horários disponíveis nesta semana.
        </p>

        <div
          v-for="day in days"
          :key="day.date"
          class="rounded-md border border-border bg-white p-4"
        >
          <h3 class="text-sm font-medium text-ink">
            {{ formatDayLabel(day.date) }}
          </h3>
          <div class="mt-3 flex flex-wrap gap-2">
            <UiButton
              v-for="slot in day.slots"
              :key="slot.startAt"
              variant="secondary"
              size="sm"
              @click="chooseSlot(slot)"
            >
              {{ formatTime(slot.startAt, zone) }}
            </UiButton>
          </div>
        </div>
      </section>

      <!-- Étape 2 : confirmer -->
      <section
        v-else-if="step === 'confirm' && selectedSlot"
        class="mt-6 space-y-5"
      >
        <h2 class="font-display text-lg text-ink">
          Confirme a sua marcação
        </h2>

        <dl class="space-y-2 rounded-md border border-border bg-white p-4 text-sm">
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Serviço
            </dt>
            <dd class="text-right text-ink">
              {{ offering.name }}
            </dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Profissional
            </dt>
            <dd class="text-right text-ink">
              {{ chosenResource ? chosenResource.name : "Sem preferência" }}
            </dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Quando
            </dt>
            <dd class="text-right text-ink">
              {{ formatLongDateTime(selectedSlot.startAt, zone) }}
            </dd>
          </div>
          <div
            v-if="terms"
            class="flex justify-between gap-4"
          >
            <dt class="text-ink/70">
              Duração
            </dt>
            <dd class="text-right text-ink">
              {{ formatDurationRange(terms.durationMin, terms.durationMax) }}
            </dd>
          </div>
          <div
            v-if="terms"
            class="flex justify-between gap-4"
          >
            <dt class="text-ink/70">
              Preço
            </dt>
            <dd class="text-right text-ink">
              {{ formatPriceRange(terms.priceMin, terms.priceMax) }}
            </dd>
          </div>
        </dl>

        <p class="text-sm text-ink/70">
          {{
            salon.autoConfirm
              ? "A marcação é confirmada de imediato."
              : "O salão confirma o pedido depois de o receber; será avisado por email."
          }}
          {{ cancellationNote(salon.cancellationDeadlineHours) }}
        </p>

        <template v-if="!auth.isAuthenticated">
          <UiAlert variant="info">
            Para concluir a marcação, inicie sessão ou crie uma conta.
          </UiAlert>
          <div class="flex flex-wrap gap-2">
            <UiButton @click="goToLogin">
              Iniciar sessão
            </UiButton>
            <UiButton
              variant="secondary"
              @click="goToRegister"
            >
              Criar conta
            </UiButton>
            <UiButton
              variant="secondary"
              @click="backToSlots"
            >
              Alterar horário
            </UiButton>
          </div>
        </template>

        <form
          v-else
          class="space-y-5"
          @submit.prevent="handleSubmit"
        >
          <div
            v-if="isUnverified"
            class="space-y-2"
          >
            <UiAlert variant="info">
              Ainda não confirmou o seu endereço de email, o que é necessário para marcar.
              Clique na ligação que lhe enviámos por email.
            </UiAlert>
            <div class="flex flex-wrap gap-2">
              <UiButton
                variant="secondary"
                size="sm"
                @click="goToAccount"
              >
                Ir para a minha conta
              </UiButton>
              <UiButton
                variant="secondary"
                size="sm"
                @click="refreshAccount"
              >
                Já confirmei, atualizar
              </UiButton>
            </div>
          </div>

          <UiInput
            v-model="phone"
            type="tel"
            label="Telemóvel (opcional)"
            hint="Permite ao salão contactá-lo se necessário."
            autocomplete="tel"
            maxlength="30"
          />

          <UiCheckbox
            v-model="consent"
            :label="consentLabel"
            hint="Obrigatório na primeira marcação neste salão."
          />

          <UiAlert
            v-if="submitError"
            variant="error"
          >
            {{ submitError }}
          </UiAlert>

          <div class="flex flex-wrap gap-2">
            <UiButton
              type="submit"
              :loading="isSubmitting"
              :disabled="isUnverified"
            >
              {{ salon.autoConfirm ? "Confirmar marcação" : "Enviar pedido de marcação" }}
            </UiButton>
            <UiButton
              variant="secondary"
              @click="backToSlots"
            >
              Alterar horário
            </UiButton>
          </div>
        </form>
      </section>

      <!-- Étape 3 : résultat -->
      <section
        v-else-if="result"
        class="mt-6 space-y-5"
      >
        <UiAlert :variant="isConfirmed ? 'success' : 'info'">
          {{
            isConfirmed
              ? "Marcação confirmada. Enviámos-lhe um email com os detalhes."
              : "Pedido de marcação enviado. O salão vai confirmá-lo e será avisado por email."
          }}
        </UiAlert>

        <dl class="space-y-2 rounded-md border border-border bg-white p-4 text-sm">
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Serviço
            </dt>
            <dd class="text-right text-ink">
              {{ result.serviceName }}
            </dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Profissional
            </dt>
            <dd class="text-right text-ink">
              {{ result.resourceName }}
            </dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Quando
            </dt>
            <dd class="text-right text-ink">
              {{ formatLongDateTime(result.startAt, zone) }}
            </dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Duração
            </dt>
            <dd class="text-right text-ink">
              {{ formatDuration(result.durationMinutes) }}
            </dd>
          </div>
          <div class="flex justify-between gap-4">
            <dt class="text-ink/70">
              Preço
            </dt>
            <dd class="text-right text-ink">
              {{ formatPrice(result.priceCents) }}
            </dd>
          </div>
        </dl>

        <p class="text-sm text-ink/70">
          {{ cancellationNote(salon.cancellationDeadlineHours) }}
        </p>

        <div class="flex flex-wrap gap-2">
          <UiButton @click="goToSalon">
            Fazer outra marcação
          </UiButton>
          <UiButton
            variant="secondary"
            @click="goToAccount"
          >
            A minha conta
          </UiButton>
        </div>
      </section>
    </template>
  </AccountLayout>
</template>
