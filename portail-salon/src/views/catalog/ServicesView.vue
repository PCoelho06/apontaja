<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";

import ServiceLinksPanel from "@/components/catalog/ServiceLinksPanel.vue";
import { useCatalogAccess } from "@/composables/useCatalogAccess";
import { ApiError } from "@/lib/apiClient";
import {
  centsToInput,
  eurosToCents,
  formatCents,
  formatDuration,
} from "@/lib/catalogFormat";
import { errorMessage } from "@/lib/errorMessage";
import {
  useSalonServiceStore,
  type SalonServiceItem,
  type ServicePayload,
} from "@/stores/salonService";

const route = useRoute();
const store = useSalonServiceStore();
const salonId = computed(() => String(route.params.salonId));
const { canManage } = useCatalogAccess(() => salonId.value);

const loading = ref(true);
const pageError = ref<string | null>(null);
const editingId = ref<string | null>(null);
const expandedId = ref<string | null>(null);
const form = reactive({ name: "", description: "", duration: "60", price: "" });
const formError = ref<string | null>(null);
const fieldErrors = ref<Record<string, string>>({});
const submitting = ref(false);

async function load() {
  loading.value = true;
  pageError.value = null;
  expandedId.value = null;
  try {
    await store.fetchServices(salonId.value);
  } catch (e) {
    pageError.value = errorMessage(e, "Impossible de charger les prestations.");
  } finally {
    loading.value = false;
  }
}

watch(salonId, load, { immediate: true });

function resetForm() {
  editingId.value = null;
  form.name = "";
  form.description = "";
  form.duration = "60";
  form.price = "";
  formError.value = null;
  fieldErrors.value = {};
}

function startEdit(service: SalonServiceItem) {
  editingId.value = service.serviceId;
  form.name = service.name;
  form.description = service.description ?? "";
  form.duration = String(service.defaultDurationMinutes);
  form.price = centsToInput(service.defaultPriceCents);
  formError.value = null;
  fieldErrors.value = {};
}

function buildPayload(): ServicePayload | null {
  const errors: Record<string, string> = {};
  const name = form.name.trim();
  if (!name) {
    errors.name = "Le nom est obligatoire.";
  }
  const duration = Number(form.duration);
  if (!Number.isInteger(duration) || duration < 1 || duration > 1440) {
    errors.defaultDurationMinutes = "Durée entière entre 1 et 1440 minutes.";
  }
  const price = eurosToCents(form.price);
  if (price === null) {
    errors.defaultPriceCents = "Prix invalide (ex. 25 ou 25,50).";
  }
  fieldErrors.value = errors;
  if (Object.keys(errors).length > 0 || price === null) {
    return null;
  }
  return {
    name,
    description: form.description.trim() || null,
    defaultDurationMinutes: duration,
    defaultPriceCents: price,
  };
}

async function submit() {
  formError.value = null;
  const payload = buildPayload();
  if (!payload) {
    return;
  }
  submitting.value = true;
  try {
    if (editingId.value) {
      await store.updateService(salonId.value, editingId.value, payload);
    } else {
      await store.createService(salonId.value, payload);
    }
    resetForm();
  } catch (e) {
    formError.value = errorMessage(e, "Enregistrement impossible.");
    fieldErrors.value = e instanceof ApiError ? (e.fieldErrors ?? {}) : {};
  } finally {
    submitting.value = false;
  }
}

async function remove(service: SalonServiceItem) {
  if (!globalThis.confirm(`Supprimer « ${service.name} » ?`)) {
    return;
  }
  pageError.value = null;
  try {
    await store.removeService(salonId.value, service.serviceId);
    if (editingId.value === service.serviceId) {
      resetForm();
    }
    if (expandedId.value === service.serviceId) {
      expandedId.value = null;
    }
  } catch (e) {
    pageError.value = errorMessage(e, "Suppression impossible.");
  }
}

function toggleLinks(service: SalonServiceItem) {
  expandedId.value =
    expandedId.value === service.serviceId ? null : service.serviceId;
}
</script>

<template>
  <section class="space-y-6">
    <p
      v-if="pageError"
      role="alert"
      class="text-red-600"
    >
      {{ pageError }}
    </p>
    <p
      v-if="loading"
      class="text-gray-500"
    >
      Chargement…
    </p>

    <ul
      v-else-if="store.services.length > 0"
      class="divide-y rounded-md border"
    >
      <li
        v-for="service in store.services"
        :key="service.serviceId"
        class="space-y-3 p-3"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="font-medium">
              {{ service.name }}
            </p>
            <p class="text-sm text-gray-500">
              {{ formatDuration(service.defaultDurationMinutes) }} ·
              {{ formatCents(service.defaultPriceCents) }}
            </p>
            <p
              v-if="service.description"
              class="text-sm text-gray-600"
            >
              {{ service.description }}
            </p>
          </div>
          <div class="flex shrink-0 gap-2">
            <button
              type="button"
              class="text-sm underline"
              @click="toggleLinks(service)"
            >
              {{ expandedId === service.serviceId ? "Masquer" : "Ressources" }}
            </button>
            <template v-if="canManage">
              <button
                type="button"
                class="text-sm underline"
                @click="startEdit(service)"
              >
                Modifier
              </button>
              <button
                type="button"
                class="text-sm text-red-600 underline"
                @click="remove(service)"
              >
                Supprimer
              </button>
            </template>
          </div>
        </div>

        <ServiceLinksPanel
          v-if="expandedId === service.serviceId"
          :salon-id="salonId"
          :service="service"
          :can-manage="canManage"
        />
      </li>
    </ul>
    <p
      v-else
      class="text-gray-500"
    >
      Aucune prestation pour le moment.
    </p>

    <form
      v-if="canManage"
      class="space-y-3 rounded-md border p-4"
      @submit.prevent="submit"
    >
      <h2 class="font-semibold">
        {{ editingId ? "Modifier la prestation" : "Nouvelle prestation" }}
      </h2>

      <label class="block text-sm">
        Nom
        <input
          v-model="form.name"
          type="text"
          maxlength="100"
          class="mt-1 block w-full rounded border px-2 py-1"
        >
        <span
          v-if="fieldErrors.name"
          class="text-red-600"
        >
          {{ fieldErrors.name }}
        </span>
      </label>

      <label class="block text-sm">
        Description (optionnelle)
        <textarea
          v-model="form.description"
          maxlength="1000"
          rows="2"
          class="mt-1 block w-full rounded border px-2 py-1"
        />
      </label>

      <div class="grid grid-cols-2 gap-3">
        <label class="block text-sm">
          Durée (minutes)
          <input
            v-model="form.duration"
            type="text"
            inputmode="numeric"
            class="mt-1 block w-full rounded border px-2 py-1"
          >
          <span
            v-if="fieldErrors.defaultDurationMinutes"
            class="text-red-600"
          >
            {{ fieldErrors.defaultDurationMinutes }}
          </span>
        </label>
        <label class="block text-sm">
          Prix (€)
          <input
            v-model="form.price"
            type="text"
            inputmode="decimal"
            placeholder="25,00"
            class="mt-1 block w-full rounded border px-2 py-1"
          >
          <span
            v-if="fieldErrors.defaultPriceCents"
            class="text-red-600"
          >
            {{ fieldErrors.defaultPriceCents }}
          </span>
        </label>
      </div>

      <p
        v-if="formError"
        role="alert"
        class="text-sm text-red-600"
      >
        {{ formError }}
      </p>

      <div class="flex gap-2">
        <button
          type="submit"
          :disabled="submitting"
          class="rounded bg-gray-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        >
          {{ editingId ? "Enregistrer" : "Ajouter" }}
        </button>
        <button
          v-if="editingId"
          type="button"
          class="rounded border px-3 py-1.5 text-sm"
          @click="resetForm"
        >
          Annuler
        </button>
      </div>
    </form>
    <p
      v-else
      class="text-sm text-gray-500"
    >
      Lecture seule : seuls les gérants et propriétaires modifient le catalogue.
    </p>
  </section>
</template>
