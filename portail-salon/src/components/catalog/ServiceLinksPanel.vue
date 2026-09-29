<script setup lang="ts">
import { computed, ref, watch } from "vue";

import {
  centsToInput,
  eurosToCents,
  formatCents,
  formatDuration,
} from "@/lib/catalogFormat";
import { errorMessage } from "@/lib/errorMessage";
import { useResourceStore } from "@/stores/resource";
import {
  useSalonServiceStore,
  type LinkOverrides,
  type SalonServiceItem,
  type ServiceResourceLink,
} from "@/stores/salonService";

const props = defineProps<{
  salonId: string;
  service: SalonServiceItem;
  canManage: boolean;
}>();

const resourceStore = useResourceStore();
const serviceStore = useSalonServiceStore();

const error = ref<string | null>(null);
const submitting = ref(false);
const editing = ref(false);
const selectedResourceId = ref("");
const priceInput = ref("");
const durationInput = ref("");

const links = computed(() => serviceStore.links[props.service.serviceId] ?? []);

const selectableResources = computed(() =>
  editing.value
    ? resourceStore.resources.filter(
        (r) => r.resourceId === selectedResourceId.value,
      )
    : resourceStore.resources.filter(
        (r) => !links.value.some((l) => l.resourceId === r.resourceId),
      ),
);

function resourceName(resourceId: string): string {
  return (
    resourceStore.resources.find((r) => r.resourceId === resourceId)?.name ??
    resourceId
  );
}

async function loadLinks() {
  error.value = null;
  try {
    await Promise.all([
      serviceStore.fetchLinks(props.salonId, props.service.serviceId),
      resourceStore.fetchResources(props.salonId),
    ]);
  } catch (e) {
    error.value = errorMessage(e, "Impossible de charger les associations.");
  }
}

// Recharge aussi quand les valeurs par défaut changent (effectifs recalculés).
watch(
  () => [
    props.service.serviceId,
    props.service.defaultPriceCents,
    props.service.defaultDurationMinutes,
  ],
  loadLinks,
  { immediate: true },
);

function resetForm() {
  editing.value = false;
  selectedResourceId.value = "";
  priceInput.value = "";
  durationInput.value = "";
}

function startEdit(link: ServiceResourceLink) {
  editing.value = true;
  selectedResourceId.value = link.resourceId;
  priceInput.value =
    link.overridePriceCents === null ? "" : centsToInput(link.overridePriceCents);
  durationInput.value =
    link.overrideDurationMinutes === null
      ? ""
      : String(link.overrideDurationMinutes);
  error.value = null;
}

function parseOverrides(): LinkOverrides | null {
  let price: number | null = null;
  if (priceInput.value.trim() !== "") {
    price = eurosToCents(priceInput.value);
    if (price === null) {
      error.value = "Prix de surcharge invalide (ex. 25 ou 25,50).";
      return null;
    }
  }
  let duration: number | null = null;
  if (durationInput.value.trim() !== "") {
    duration = Number(durationInput.value);
    if (!Number.isInteger(duration) || duration < 1 || duration > 1440) {
      error.value = "Durée de surcharge : entier entre 1 et 1440 minutes.";
      return null;
    }
  }
  return { overridePriceCents: price, overrideDurationMinutes: duration };
}

async function save() {
  error.value = null;
  if (!selectedResourceId.value) {
    error.value = "Choisissez une ressource.";
    return;
  }
  const overrides = parseOverrides();
  if (!overrides) {
    return;
  }
  submitting.value = true;
  try {
    await serviceStore.saveLink(
      props.salonId,
      props.service.serviceId,
      selectedResourceId.value,
      overrides,
    );
    resetForm();
  } catch (e) {
    error.value = errorMessage(e, "Association impossible.");
  } finally {
    submitting.value = false;
  }
}

async function remove(link: ServiceResourceLink) {
  error.value = null;
  try {
    await serviceStore.removeLink(
      props.salonId,
      props.service.serviceId,
      link.resourceId,
    );
    if (selectedResourceId.value === link.resourceId) {
      resetForm();
    }
  } catch (e) {
    error.value = errorMessage(e, "Retrait impossible.");
  }
}
</script>

<template>
  <div class="space-y-3 rounded-md bg-gray-50 p-3">
    <p class="text-sm font-medium">
      Ressources pouvant réaliser cette prestation
    </p>

    <ul
      v-if="links.length > 0"
      class="divide-y text-sm"
    >
      <li
        v-for="link in links"
        :key="link.resourceId"
        class="flex items-center justify-between gap-3 py-2"
      >
        <div>
          <span class="font-medium">{{ resourceName(link.resourceId) }}</span>
          <span class="text-gray-600">
            — {{ formatDuration(link.effectiveDurationMinutes) }} ·
            {{ formatCents(link.effectivePriceCents) }}
          </span>
          <span
            v-if="
              link.overridePriceCents !== null ||
                link.overrideDurationMinutes !== null
            "
            class="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-xs"
          >
            surcharge
          </span>
        </div>
        <div
          v-if="canManage"
          class="flex gap-2"
        >
          <button
            type="button"
            class="underline"
            @click="startEdit(link)"
          >
            Modifier
          </button>
          <button
            type="button"
            class="text-red-600 underline"
            @click="remove(link)"
          >
            Retirer
          </button>
        </div>
      </li>
    </ul>
    <p
      v-else
      class="text-sm text-gray-500"
    >
      Aucune ressource associée : cette prestation n'est pas réservable.
    </p>

    <form
      v-if="canManage"
      class="grid grid-cols-1 gap-2 text-sm sm:grid-cols-4"
      @submit.prevent="save"
    >
      <select
        v-model="selectedResourceId"
        :disabled="editing"
        class="rounded border px-2 py-1"
      >
        <option
          value=""
          disabled
        >
          Ressource…
        </option>
        <option
          v-for="resource in selectableResources"
          :key="resource.resourceId"
          :value="resource.resourceId"
        >
          {{ resource.name }}
        </option>
      </select>
      <input
        v-model="priceInput"
        type="text"
        inputmode="decimal"
        :placeholder="`Prix (défaut ${centsToInput(service.defaultPriceCents)})`"
        class="rounded border px-2 py-1"
      >
      <input
        v-model="durationInput"
        type="text"
        inputmode="numeric"
        :placeholder="`Durée (défaut ${service.defaultDurationMinutes} min)`"
        class="rounded border px-2 py-1"
      >
      <div class="flex gap-2">
        <button
          type="submit"
          :disabled="submitting"
          class="rounded bg-gray-900 px-3 py-1 text-white disabled:opacity-50"
        >
          {{ editing ? "Enregistrer" : "Associer" }}
        </button>
        <button
          v-if="editing"
          type="button"
          class="rounded border px-3 py-1"
          @click="resetForm"
        >
          Annuler
        </button>
      </div>
    </form>

    <p
      v-if="error"
      role="alert"
      class="text-sm text-red-600"
    >
      {{ error }}
    </p>
  </div>
</template>
