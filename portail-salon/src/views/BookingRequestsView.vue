<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiBadge, UiButton, UiDialog } from "@apontaja/ui-kit";

import { ApiError } from "@apontaja/web-core/api-client";
import { errorMessage } from "@apontaja/web-core/error-message";
import { ageLabel } from "@/lib/bookingRequests";
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
  return customer ? fullName(customer) : "Client inconnu";
}

function serviceName(id: string): string {
  return serviceStore.services.find((s) => s.serviceId === id)?.name ?? "Prestation";
}

function resourceName(id: string): string {
  return resourceStore.resources.find((r) => r.resourceId === id)?.name ?? "Ressource";
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
    error.value = errorMessage(e, "Impossible de charger les demandes.");
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
    error.value = errorMessage(e, "Impossible d'actualiser les demandes.");
  } finally {
    refreshing.value = false;
  }
}

/** 409 : la demande a changé (le client l'a annulée, un collègue l'a traitée) → message clair + rechargement. */
async function failure(e: unknown, fallback: string): Promise<string> {
  if (e instanceof ApiError && e.status === 409) {
    try {
      await refresh();
    } catch {
      // La liste reste celle d'avant ; le message suffit.
    }
    return "Cette demande a changé entre-temps (annulée par le client ou déjà traitée). La liste a été actualisée.";
  }
  return errorMessage(e, fallback);
}

async function confirm(request: AppointmentItem) {
  busyId.value = request.appointmentId;
  error.value = null;
  notice.value = null;
  try {
    await requestsStore.confirmRequest(salonId.value, request.appointmentId);
    notice.value = `Rendez-vous confirmé pour ${customerName(request.customerProfileId)}. Un email lui a été envoyé s'il a une adresse.`;
  } catch (e) {
    error.value = await failure(e, "La confirmation a échoué.");
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
    notice.value = `Demande refusée pour ${customerName(request.customerProfileId)}. Un email lui a été envoyé s'il a une adresse.`;
    declineOpen.value = false;
  } catch (e) {
    declineError.value = await failure(e, "Le refus a échoué.");
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
          ← {{ salonStore.currentSalon?.name ?? "Salon" }}
        </RouterLink>
        <div class="mt-2 flex items-center justify-between gap-3">
          <h1 class="font-display text-2xl text-ink">
            Demandes à valider
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
            Actualiser
          </UiButton>
        </div>
        <p class="mt-1 text-sm text-ink/70">
          Réservations faites en ligne, en attente de votre décision. Le client
          est prévenu par email lorsque vous confirmez ou refusez. Heures du
          salon<span v-if="timeZone"> ({{ timeZone }})</span>.
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
        Chargement…
      </p>

      <template v-else>
        <p
          v-if="requestsStore.count === 0"
          class="text-ink/60"
        >
          Aucune demande en attente.
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
                {{ serviceName(request.serviceId) }} avec
                {{ resourceName(request.resourceId) }} ·
                {{ formatDuration(request.durationAtBookingMinutes) }} ·
                {{ formatCents(request.priceAtBookingCents) }}
              </p>
              <p class="text-xs text-ink/60">
                Demandé {{ ageLabel(request.createdAt, now) }}
              </p>
            </div>
            <div class="flex gap-2">
              <UiButton
                size="sm"
                :loading="busyId === request.appointmentId"
                :disabled="busyId !== null"
                @click="confirm(request)"
              >
                Confirmer
              </UiButton>
              <UiButton
                variant="secondary"
                size="sm"
                :disabled="busyId !== null"
                @click="askDecline(request)"
              >
                Refuser
              </UiButton>
            </div>
          </li>
        </ul>
        <RouterLink
          :to="{ name: 'agenda', params: { salonId } }"
          class="inline-block text-sm text-wine hover:underline"
        >
          Voir l'agenda
        </RouterLink>
      </template>

      <UiDialog
        v-model:open="declineOpen"
        title="Refuser la demande"
      >
        <div
          v-if="declining"
          class="space-y-4 text-sm text-ink"
        >
          <p>
            Refuser la demande de
            <strong>{{ customerName(declining.customerProfileId) }}</strong>
            le {{ visitLabel(declining.startAt, timeZone) }} ? Le créneau sera
            libéré et le client en sera informé par email.
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
              Garder la demande
            </UiButton>
            <UiButton
              variant="danger"
              :loading="busyId === declining.appointmentId"
              @click="confirmDecline"
            >
              Refuser
            </UiButton>
          </div>
        </div>
      </UiDialog>
    </div>
  </main>
</template>
