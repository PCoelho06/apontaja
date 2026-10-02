<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import {
  UiAlert,
  UiBadge,
  UiButton,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

import DayGrid from "@/components/agenda/DayGrid.vue";
import AppointmentCreateDialog from "@/components/agenda/AppointmentCreateDialog.vue";
import AppointmentDetailDialog from "@/components/agenda/AppointmentDetailDialog.vue";
import {
  buildColumns,
  formatDay,
  formatMinutes,
  shiftDate,
  todayIn,
} from "@/lib/agenda";
import {
  STATUS_LABELS,
  STATUS_ORDER,
  STATUS_TONES,
} from "@/lib/appointmentStatus";
import { errorMessage } from "@/lib/errorMessage";
import { formatInstant, isValidTimeZone } from "@/lib/zonedTime";
import { useAgendaStore } from "@/stores/agenda";
import { useAuthStore } from "@/stores/auth";
import { useCustomerStore } from "@/stores/customer";
import { useResourceStore } from "@/stores/resource";
import { useSalonStore } from "@/stores/salon";
import { useSalonServiceStore } from "@/stores/salonService";
import { useStaffStore } from "@/stores/staff";

const route = useRoute();
const salonId = computed(() => String(route.params.salonId));

const authStore = useAuthStore();
const salonStore = useSalonStore();
const resourceStore = useResourceStore();
const staffStore = useStaffStore();
const customerStore = useCustomerStore();
const serviceStore = useSalonServiceStore();
const agendaStore = useAgendaStore();

const ready = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const date = ref("");
const filter = ref("all"); // "all", "mine" ou l'identifiant d'une ressource
const showCancelled = ref(false);
const notice = ref<string | null>(null);

const timeZone = computed(() => salonStore.currentSalon?.timezone ?? "");
const timeZoneValid = computed(() => isValidTimeZone(timeZone.value));

/** Ressource liée au compte connecté (lien membre de l'équipe ↔ ressource, tranche 8c). */
const myResource = computed(() => {
  const accountId = authStore.account?.accountId;
  const membership = staffStore.members.find((m) => m.accountId === accountId);
  if (!membership) {
    return null;
  }
  return (
    resourceStore.resources.find(
      (r) => r.staffMembershipId === membership.staffMembershipId,
    ) ?? null
  );
});

const visibleResources = computed(() => {
  if (filter.value === "mine") {
    return myResource.value ? [myResource.value] : [];
  }
  if (filter.value === "all") {
    return resourceStore.resources;
  }
  return resourceStore.resources.filter((r) => r.resourceId === filter.value);
});

const customerNames = computed(
  () =>
    new Map(
      customerStore.customers.map((c) => [
        c.customerProfileId,
        `${c.firstName} ${c.lastName}`,
      ]),
    ),
);
const serviceNames = computed(
  () => new Map(serviceStore.services.map((s) => [s.serviceId, s.name])),
);

const model = computed(() => {
  if (!timeZoneValid.value || !date.value) {
    return null;
  }
  const appointments = agendaStore.appointments
    .filter((a) => showCancelled.value || a.status !== "CANCELLED")
    .map((a) => ({
      appointmentId: a.appointmentId,
      resourceId: a.resourceId,
      startAt: a.startAt,
      endAt: a.endAt,
      status: a.status,
      customerName:
        customerNames.value.get(a.customerProfileId) ?? "Client inconnu",
      serviceName:
        serviceNames.value.get(a.serviceId) ?? "Prestation supprimée",
    }));
  return buildColumns({
    date: date.value,
    timeZone: timeZone.value,
    resources: visibleResources.value.map((r) => ({
      resourceId: r.resourceId,
      name: r.name,
    })),
    appointments,
    salonSlots: agendaStore.salonSlots,
    resourceSlots: agendaStore.resourceSlots,
    closures: agendaStore.closures,
  });
});

const appointmentCount = computed(() =>
  model.value
    ? model.value.columns.reduce((sum, c) => sum + c.appointments.length, 0)
    : 0,
);

async function loadDay() {
  if (!timeZoneValid.value || !date.value) {
    return;
  }
  loading.value = true;
  error.value = null;
  try {
    await agendaStore.loadDay(
      salonId.value,
      date.value,
      timeZone.value,
      resourceStore.resources.map((r) => r.resourceId),
    );
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "Impossible de charger l'agenda.";
  } finally {
    loading.value = false;
  }
}

function setDate(next: string) {
  notice.value = null;
  date.value = next;
  if (next) {
    loadDay();
  }
}

function shift(days: number) {
  if (date.value) {
    setDate(shiftDate(date.value, days));
  }
}

const createOpen = ref(false);
const createStart = ref("");
const createResourceId = ref<string | null>(null);
const detailOpen = ref(false);
const selectedId = ref<string | null>(null);

const selectedAppointment = computed(
  () =>
    agendaStore.appointments.find(
      (a) => a.appointmentId === selectedId.value,
    ) ?? null,
);

/** `minutes` null : début par défaut (ouverture de la grille). */
function openCreate(resourceId: string | null, minutes: number | null) {
  const start = minutes ?? model.value?.axis.start ?? 9 * 60;
  createStart.value = `${date.value}T${formatMinutes(start)}`;
  createResourceId.value =
    resourceId ??
    myResource.value?.resourceId ??
    visibleResources.value[0]?.resourceId ??
    null;
  createOpen.value = true;
}

function openDetail(appointmentId: string) {
  selectedId.value = appointmentId;
  detailOpen.value = true;
}

function onRescheduled(startAt: string) {
  notice.value = `Rendez-vous déplacé : ${formatInstant(startAt, timeZone.value)}.`;
}

watch(
  salonId,
  async (id) => {
    ready.value = false;
    error.value = null;
    try {
      await Promise.all([
        salonStore.fetchSalon(id),
        resourceStore.fetchResources(id),
        staffStore.fetchMembers(id),
        customerStore.fetchCustomers(id),
        serviceStore.fetchServices(id),
      ]);
      if (id !== salonId.value) {
        return;
      }
      date.value = timeZoneValid.value ? todayIn(timeZone.value) : "";
      filter.value = myResource.value ? "mine" : "all";
      ready.value = true;
      await loadDay();
    } catch (e) {
      error.value = errorMessage(e, "Impossible de charger l'agenda.");
    }
  },
  { immediate: true },
);
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-8">
    <div class="mx-auto max-w-6xl space-y-6">
      <div>
        <RouterLink
          :to="{ name: 'salon-detail', params: { salonId } }"
          class="text-sm text-wine hover:underline"
        >
          ← {{ salonStore.currentSalon?.name ?? "Salon" }}
        </RouterLink>
        <h1 class="mt-2 font-display text-2xl text-ink">
          Agenda
        </h1>
      </div>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>
      <p
        v-if="!ready && !error"
        class="text-sm text-ink/70"
      >
        Chargement…
      </p>

      <template v-else-if="ready">
        <UiAlert
          v-if="!timeZoneValid"
          variant="error"
        >
          Le fuseau horaire du salon (« {{ timeZone }} ») n'est pas un
          identifiant valide (ex. Europe/Paris) : l'agenda ne peut pas être
          affiché.
        </UiAlert>

        <template v-else>
          <div class="flex flex-wrap items-end gap-3">
            <div class="flex gap-2">
              <UiButton
                variant="secondary"
                aria-label="Jour précédent"
                @click="shift(-1)"
              >
                ‹
              </UiButton>
              <UiButton
                variant="secondary"
                @click="setDate(todayIn(timeZone))"
              >
                Aujourd'hui
              </UiButton>
              <UiButton
                variant="secondary"
                aria-label="Jour suivant"
                @click="shift(1)"
              >
                ›
              </UiButton>
            </div>
            <div class="w-44">
              <UiInput
                :model-value="date"
                type="date"
                label="Jour"
                @update:model-value="setDate"
              />
            </div>
            <div class="w-64">
              <UiSelect
                v-model="filter"
                label="Ressources"
              >
                <option value="all">
                  Toutes les ressources
                </option>
                <option
                  v-if="myResource"
                  value="mine"
                >
                  Mon agenda ({{ myResource.name }})
                </option>
                <option
                  v-for="resource in resourceStore.resources"
                  :key="resource.resourceId"
                  :value="resource.resourceId"
                >
                  {{ resource.name }}
                </option>
              </UiSelect>
            </div>
            <label class="flex items-center gap-2 pb-2 text-sm text-ink">
              <input
                v-model="showCancelled"
                type="checkbox"
              >
              Afficher les rendez-vous annulés
            </label>
            <UiButton
              class="ml-auto"
              :disabled="!date || visibleResources.length === 0"
              @click="openCreate(null, null)"
            >
              Nouveau rendez-vous
            </UiButton>
          </div>

          <div class="flex flex-wrap items-center justify-between gap-3">
            <p
              v-if="date"
              class="text-lg capitalize text-ink"
            >
              {{ formatDay(date) }}
              <span class="text-sm normal-case text-ink/60">
                · {{ appointmentCount }} rendez-vous
              </span>
            </p>
            <div class="flex flex-wrap gap-2">
              <UiBadge
                v-for="status in STATUS_ORDER"
                :key="status"
                :tone="STATUS_TONES[status]"
              >
                {{ STATUS_LABELS[status] }}
              </UiBadge>
            </div>
          </div>

          <UiAlert
            v-if="notice"
            variant="success"
          >
            {{ notice }}
          </UiAlert>

          <UiAlert v-if="visibleResources.length === 0">
            Aucune ressource à afficher.
            <RouterLink
              :to="{ name: 'catalog-resources', params: { salonId } }"
              class="underline"
            >
              Gérer les ressources
            </RouterLink>
          </UiAlert>
          <div
            v-else-if="model"
            :class="{ 'opacity-60': loading }"
          >
            <DayGrid
              :columns="model.columns"
              :axis="model.axis"
              @select-slot="openCreate"
              @select-appointment="openDetail"
            />
          </div>
          <AppointmentCreateDialog
            v-model:open="createOpen"
            :salon-id="salonId"
            :time-zone="timeZone"
            :resources="resourceStore.resources"
            :initial-resource-id="createResourceId"
            :initial-start="createStart"
            @created="loadDay"
          />
          <AppointmentDetailDialog
            v-model:open="detailOpen"
            :salon-id="salonId"
            :time-zone="timeZone"
            :appointment="selectedAppointment"
            @changed="loadDay"
            @rescheduled="onRescheduled"
          />
        </template>
      </template>
    </div>
  </main>
</template>
