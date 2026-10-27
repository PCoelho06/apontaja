<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiBadge, UiButton, UiDialog } from "@apontaja/ui-kit";

import { ageLabel } from "@/lib/bookingRequests";
import {
  describeRequestActionError,
  describeRequestsLoadError,
  shouldReloadRequests,
} from "@/lib/bookingRequestErrors";
import { formatCents, formatDuration } from "@/lib/catalogFormat";
import { fullName, visitLabel } from "@/lib/customerFormat";
import type { AppointmentItem } from "@/stores/agenda";
import { useBookingRequestsStore } from "@/stores/bookingRequests";
import { useCustomerStore } from "@/stores/customer";
import { useResourceStore } from "@/stores/resource";
import { useSalonStore } from "@/stores/salon";
import { useSalonServiceStore } from "@/stores/salonService";

const route = useRoute();
const salonId = computed(() => String(route.params.salonId));

const salonStore = useSalonStore();
const requestsStore = useBookingRequestsStore();
const customerStore = useCustomerStore();
const serviceStore = useSalonServiceStore();
const resourceStore = useResourceStore();

const loading = ref(true);
const refreshing = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const busyId = ref<string | null>(null);
const now = ref(new Date());

const timeZone = computed(() => salonStore.currentSalon?.timezone ?? "");

const declineOpen = ref(false);
const declining = ref<AppointmentItem | null>(null);
const declineError = ref<string | null>(null);

function customerName(id: string): string {
  const customer = customerStore.byId[id];
  return customer ? fullName(customer) : "Cliente desconhecido";
}

function serviceName(id: string): string {
  return serviceStore.services.find((s) => s.serviceId === id)?.name ?? "Serviço eliminado";
}

function resourceName(id: string): string {
  return resourceStore.resources.find((r) => r.resourceId === id)?.name ?? "Recurso desconhecido";
}

async function refresh() {
  now.value = new Date();
  await requestsStore.fetchRequests(salonId.value, now.value);
  await customerStore.ensureCustomers(
    salonId.value,
    requestsStore.requests.map((r) => r.customerProfileId),
  );
}

async function load(id: string) {
  loading.value = true;
  error.value = null;
  notice.value = null;
  try {
    await Promise.all([
      salonStore.fetchSalon(id),
      serviceStore.fetchServices(id),
      resourceStore.fetchResources(id),
    ]);
    await refresh();
  } catch (e) {
    error.value = describeRequestsLoadError(e, "load");
  } finally {
    loading.value = false;
  }
}

watch(salonId, load, { immediate: true });

async function reload() {
  refreshing.value = true;
  error.value = null;
  try {
    await refresh();
  } catch (e) {
    error.value = describeRequestsLoadError(e, "refresh");
  } finally {
    refreshing.value = false;
  }
}

/** 404/409 : la demande a changé (le client l'a annulée, un collègue l'a traitée) → message clair + rechargement. */
async function failure(
  e: unknown,
  action: "confirm" | "decline",
): Promise<string> {
  if (shouldReloadRequests(e)) {
    try {
      await refresh();
    } catch {
      // La liste reste celle d'avant ; le message suffit.
    }
  }
  return describeRequestActionError(e, action);
}

async function confirm(request: AppointmentItem) {
  busyId.value = request.appointmentId;
  error.value = null;
  notice.value = null;
  try {
    await requestsStore.confirmRequest(salonId.value, request.appointmentId);
    notice.value =
      `Marcação confirmada para ${customerName(request.customerProfileId)}. ` +
      "Foi enviado um email ao cliente, se tiver endereço.";
  } catch (e) {
    error.value = await failure(e, "confirm");
  } finally {
    busyId.value = null;
  }
}

function askDecline(request: AppointmentItem) {
  declining.value = request;
  declineError.value = null;
  declineOpen.value = true;
}

async function confirmDecline() {
  const request = declining.value;
  if (!request) {
    return;
  }
  busyId.value = request.appointmentId;
  declineError.value = null;
  try {
    await requestsStore.declineRequest(salonId.value, request.appointmentId);
    notice.value =
      `Pedido recusado para ${customerName(request.customerProfileId)}. ` +
      "Foi enviado um email ao cliente, se tiver endereço.";
    declineOpen.value = false;
  } catch (e) {
    declineError.value = await failure(e, "decline");
  } finally {
    busyId.value = null;
  }
}
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div>
        <RouterLink
          :to="{ name: 'salon-detail', params: { salonId } }"
          class="text-sm text-wine hover:underline"
        >
          ← {{ salonStore.currentSalon?.name ?? "Salão" }}
        </RouterLink>
        <div class="mt-2 flex items-center justify-between gap-3">
          <h1 class="font-display text-2xl text-ink">
            Pedidos por validar
            <UiBadge
              v-if="!loading && requestsStore.count > 0"
              tone="brass"
            >
              {{ requestsStore.count }}
            </UiBadge>
          </h1>
          <UiButton
            variant="secondary"
            size="sm"
            :loading="refreshing"
            @click="reload"
          >
            Atualizar
          </UiButton>
        </div>
        <p class="mt-1 text-sm text-ink/70">
          Reservas feitas online, a aguardar a sua decisão. O cliente é
          avisado por email quando confirma ou recusa. Horas do
          salão<span v-if="timeZone"> ({{ timeZone }})</span>.
        </p>
      </div>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>
      <UiAlert
        v-if="notice"
        variant="success"
      >
        {{ notice }}
      </UiAlert>
      <p
        v-if="loading"
        class="text-sm text-ink/70"
      >
        A carregar...
      </p>

      <template v-else>
        <p
          v-if="requestsStore.count === 0"
          class="text-ink/60"
        >
          Nenhum pedido em espera.
        </p>
        <ul
          v-else
          class="divide-y divide-border rounded-md border border-border bg-white"
        >
          <li
            v-for="request in requestsStore.requests"
            :key="request.appointmentId"
            class="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
          >
            <div class="text-sm">
              <p class="font-medium text-ink">
                {{ visitLabel(request.startAt, timeZone) }} ·
                {{ customerName(request.customerProfileId) }}
              </p>
              <p class="text-ink/70">
                {{ serviceName(request.serviceId) }} com
                {{ resourceName(request.resourceId) }} ·
                {{ formatDuration(request.durationAtBookingMinutes) }} ·
                {{ formatCents(request.priceAtBookingCents) }}
              </p>
              <p class="text-xs text-ink/60">
                Pedido feito {{ ageLabel(request.createdAt, now) }}
              </p>
            </div>
            <div class="flex gap-2">
              <UiButton
                size="sm"
                :loading="busyId === request.appointmentId"
                :disabled="busyId !== null"
                @click="confirm(request)"
              >
                Confirmar
              </UiButton>
              <UiButton
                variant="secondary"
                size="sm"
                :disabled="busyId !== null"
                @click="askDecline(request)"
              >
                Recusar
              </UiButton>
            </div>
          </li>
        </ul>
        <RouterLink
          :to="{ name: 'agenda', params: { salonId } }"
          class="inline-block text-sm text-wine hover:underline"
        >
          Ver a agenda
        </RouterLink>
      </template>

      <UiDialog
        v-model:open="declineOpen"
        title="Recusar pedido"
        close-label="Fechar"
      >
        <div
          v-if="declining"
          class="space-y-4 text-sm text-ink"
        >
          <p>
            Recusar o pedido de
            <strong>{{ customerName(declining.customerProfileId) }}</strong>
            para {{ visitLabel(declining.startAt, timeZone) }}? O horário será
            libertado e o cliente será informado por email.
          </p>
          <UiAlert
            v-if="declineError"
            variant="error"
          >
            {{ declineError }}
          </UiAlert>
          <div class="flex justify-end gap-2">
            <UiButton
              variant="secondary"
              :disabled="busyId !== null"
              @click="declineOpen = false"
            >
              Manter pedido
            </UiButton>
            <UiButton
              variant="danger"
              :loading="busyId === declining.appointmentId"
              @click="confirmDecline"
            >
              Recusar
            </UiButton>
          </div>
        </div>
      </UiDialog>
    </div>
  </main>
</template>



