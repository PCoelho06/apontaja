<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import SessionActions from "@/components/SessionActions.vue";
import AccountLayout from "@/layouts/AccountLayout.vue";
import {
  describeCancelError,
  describeListError,
  isStaleView,
} from "@/lib/appointmentErrors";
import { formatDuration, formatPrice } from "@/lib/format";
import type { AppointmentScope, ClientAppointment } from "@/lib/myAppointments";
import {
  CANCELLATION_CLOSED_NOTE,
  cancellableUntilNote,
  cancellationState,
  statusInfo,
  whenLabel,
} from "@/lib/myAppointments";
import { browserZone } from "@/lib/salonCalendar";
import { useAppointmentsStore } from "@/stores/appointments";

import { UiAlert, UiBadge, UiButton, UiDialog } from "@apontaja/ui-kit";

interface Notice {
  variant: "success" | "error";
  text: string;
}

const route = useRoute();
const router = useRouter();
const store = useAppointmentsStore();

const browser = browserZone();
const now = ref(new Date());

const loading = ref(false);
const loadError = ref("");
const loadingMore = ref(false);
const moreError = ref("");
const notice = ref<Notice | null>(null);
let loadToken = 0;

const rows = computed(() =>
  store.items.map((appointment) => ({
    appointment,
    status: statusInfo(appointment, now.value),
    state: cancellationState(appointment, now.value),
    when: whenLabel(
      appointment.startAt,
      appointment.salon.timezone,
      browser,
    ),
    until: cancellableUntilNote(appointment),
  })),
);

const emptyText = computed(() =>
  store.scope === "upcoming"
    ? "Não tem marcações próximas."
    : "Não tem marcações passadas.",
);

async function load(scope: AppointmentScope) {
  loadToken += 1;
  const token = loadToken;
  now.value = new Date();
  loading.value = true;
  loadError.value = "";
  moreError.value = "";
  try {
    await store.load(scope);
  } catch (error) {
    if (token === loadToken) {
      loadError.value = describeListError(error);
    }
  } finally {
    if (token === loadToken) {
      loading.value = false;
    }
  }
}

function switchScope(scope: AppointmentScope) {
  if (scope === store.scope && !loadError.value) {
    return;
  }
  notice.value = null;
  void load(scope);
}

function retry() {
  void load(store.scope);
}

async function loadMore() {
  if (loadingMore.value || loading.value) {
    return;
  }
  moreError.value = "";
  loadingMore.value = true;
  try {
    await store.loadMore();
  } catch (error) {
    moreError.value = describeListError(error);
  } finally {
    loadingMore.value = false;
  }
}

// --- Annulation -----------------------------------------------------------------------------------

const dialogOpen = ref(false);
const target = ref<ClientAppointment | null>(null);
const cancelError = ref("");
const isCancelling = ref(false);

const targetWhen = computed(() =>
  target.value
    ? whenLabel(target.value.startAt, target.value.salon.timezone, browser)
    : "",
);
const targetUntil = computed(() =>
  target.value ? cancellableUntilNote(target.value) : "",
);

function openCancel(appointment: ClientAppointment) {
  target.value = appointment;
  cancelError.value = "";
  dialogOpen.value = true;
}

function goToLogin() {
  void router.push({ name: "login", query: { redirect: route.fullPath } });
}

async function confirmCancel() {
  const appointment = target.value;
  if (!appointment || isCancelling.value) {
    return;
  }
  cancelError.value = "";
  isCancelling.value = true;
  try {
    await store.cancel(appointment.appointmentId);
    dialogOpen.value = false;
    notice.value = {
      variant: "success",
      text: "Marcação cancelada. Receberá um email de confirmação.",
    };
  } catch (error) {
    const failure = describeCancelError(error);
    if (failure.kind === "UNAUTHENTICATED") {
      goToLogin();
    } else if (isStaleView(failure.kind)) {
      // L'état affiché est périmé : on le dit puis on recharge la liste.
      dialogOpen.value = false;
      notice.value = { variant: "error", text: failure.message };
      void load(store.scope);
    } else {
      cancelError.value = failure.message;
    }
  } finally {
    isCancelling.value = false;
  }
}

onMounted(() => {
  void load("upcoming");
});
</script>

<template>
  <AccountLayout>
    <template #actions>
      <SessionActions />
    </template>

    <h1 class="font-display text-2xl text-ink">
      As minhas marcações
    </h1>

    <div
      class="mt-6 flex gap-2"
      role="group"
      aria-label="Filtrar marcações"
    >
      <UiButton
        :variant="store.scope === 'upcoming' ? 'primary' : 'secondary'"
        size="sm"
        :aria-pressed="store.scope === 'upcoming'"
        @click="switchScope('upcoming')"
      >
        Próximas
      </UiButton>
      <UiButton
        :variant="store.scope === 'past' ? 'primary' : 'secondary'"
        size="sm"
        :aria-pressed="store.scope === 'past'"
        @click="switchScope('past')"
      >
        Passadas
      </UiButton>
    </div>

    <UiAlert
      v-if="notice"
      :variant="notice.variant"
      class="mt-6"
    >
      {{ notice.text }}
    </UiAlert>

    <p
      v-if="loading"
      class="mt-6 text-sm text-ink/70"
    >
      A carregar…
    </p>

    <div
      v-else-if="loadError"
      class="mt-6 space-y-3"
    >
      <UiAlert variant="error">
        {{ loadError }}
      </UiAlert>
      <UiButton
        variant="secondary"
        size="sm"
        @click="retry"
      >
        Tentar novamente
      </UiButton>
    </div>

    <p
      v-else-if="rows.length === 0"
      class="mt-6 text-sm text-ink/70"
    >
      {{ emptyText }}
    </p>

    <template v-else>
      <ul class="mt-6 space-y-4">
        <li
          v-for="row in rows"
          :key="row.appointment.appointmentId"
          class="rounded-md border border-border bg-white p-4"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <h2 class="font-medium text-ink">
                {{ row.appointment.service.name }}
              </h2>
              <p class="text-sm text-ink/70">
                {{ row.appointment.salon.name }} · {{ row.appointment.salon.city }}
              </p>
            </div>
            <UiBadge :tone="row.status.tone">
              {{ row.status.label }}
            </UiBadge>
          </div>

          <dl class="mt-3 space-y-1 text-sm">
            <div class="flex justify-between gap-4">
              <dt class="text-ink/70">
                Quando
              </dt>
              <dd class="text-right text-ink">
                {{ row.when }}
              </dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-ink/70">
                Profissional
              </dt>
              <dd class="text-right text-ink">
                {{ row.appointment.resource.name }}
              </dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-ink/70">
                Duração
              </dt>
              <dd class="text-right text-ink">
                {{ formatDuration(row.appointment.durationMinutes) }}
              </dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-ink/70">
                Preço
              </dt>
              <dd class="text-right text-ink">
                {{ formatPrice(row.appointment.priceCents) }}
              </dd>
            </div>
          </dl>

          <div
            v-if="row.state === 'CANCELLABLE'"
            class="mt-4 flex flex-wrap items-center justify-between gap-3"
          >
            <p class="text-sm text-ink/70">
              {{ row.until }}
            </p>
            <UiButton
              variant="danger"
              size="sm"
              @click="openCancel(row.appointment)"
            >
              Cancelar
            </UiButton>
          </div>
          <p
            v-else-if="row.state === 'DEADLINE_PASSED'"
            class="mt-4 text-sm text-ink/70"
          >
            {{ CANCELLATION_CLOSED_NOTE }}
          </p>
        </li>
      </ul>

      <div
        v-if="store.hasMore"
        class="mt-6 space-y-3"
      >
        <UiAlert
          v-if="moreError"
          variant="error"
        >
          {{ moreError }}
        </UiAlert>
        <UiButton
          variant="secondary"
          :loading="loadingMore"
          @click="loadMore"
        >
          Ver mais
        </UiButton>
      </div>
    </template>

    <UiDialog
      v-model:open="dialogOpen"
      title="Cancelar marcação"
      close-label="Fechar"
    >
      <div
        v-if="target"
        class="space-y-3 text-sm text-ink"
      >
        <p>
          Quer cancelar a marcação «{{ target.service.name }}» no salão
          {{ target.salon.name }}, {{ targetWhen }}?
        </p>
        <p
          v-if="targetUntil"
          class="text-ink/70"
        >
          {{ targetUntil }}
        </p>
        <UiAlert
          v-if="cancelError"
          variant="error"
        >
          {{ cancelError }}
        </UiAlert>
      </div>
      <template #footer>
        <UiButton
          variant="secondary"
          @click="dialogOpen = false"
        >
          Manter marcação
        </UiButton>
        <UiButton
          variant="danger"
          :loading="isCancelling"
          @click="confirmCancel"
        >
          Cancelar marcação
        </UiButton>
      </template>
    </UiDialog>
  </AccountLayout>
</template>
