<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";

import {
  UiAlert,
  UiButton,
  UiInput,
  UiTextarea,
} from "@apontaja/ui-kit";

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
    <UiAlert
      v-if="pageError"
      variant="error"
    >
      {{ pageError }}
    </UiAlert>
    <p
      v-if="loading"
      class="text-sm text-ink/70"
    >
      Chargement…
    </p>

    <ul
      v-else-if="store.services.length > 0"
      class="divide-y divide-border rounded-md border border-border bg-white"
    >
      <li
        v-for="service in store.services"
        :key="service.serviceId"
        class="space-y-3 px-4 py-3"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="font-medium text-ink">
              {{ service.name }}
            </p>
            <p class="text-sm text-ink/60">
              {{ formatDuration(service.defaultDurationMinutes) }} ·
              {{ formatCents(service.defaultPriceCents) }}
            </p>
            <p
              v-if="service.description"
              class="text-sm text-ink/70"
            >
              {{ service.description }}
            </p>
          </div>
          <div class="flex shrink-0 flex-wrap justify-end gap-2">
            <UiButton
              variant="secondary"
              size="sm"
              @click="toggleLinks(service)"
            >
              {{ expandedId === service.serviceId ? "Masquer" : "Ressources" }}
            </UiButton>
            <template v-if="canManage">
              <UiButton
                variant="secondary"
                size="sm"
                @click="startEdit(service)"
              >
                Modifier
              </UiButton>
              <UiButton
                variant="danger"
                size="sm"
                @click="remove(service)"
              >
                Supprimer
              </UiButton>
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
      class="text-ink/60"
    >
      Aucune prestation pour le moment.
    </p>

    <form
      v-if="canManage"
      class="space-y-4 rounded-md border border-border bg-white p-4"
      @submit.prevent="submit"
    >
      <h2 class="text-lg font-medium text-ink">
        {{ editingId ? "Modifier la prestation" : "Nouvelle prestation" }}
      </h2>

      <UiInput
        v-model="form.name"
        label="Nom"
        maxlength="100"
        :error="fieldErrors.name"
      />

      <UiTextarea
        v-model="form.description"
        label="Description (optionnelle)"
        maxlength="1000"
        rows="2"
      />

      <div class="grid grid-cols-2 gap-4">
        <UiInput
          v-model="form.duration"
          label="Durée (minutes)"
          inputmode="numeric"
          :error="fieldErrors.defaultDurationMinutes"
        />
        <UiInput
          v-model="form.price"
          label="Prix (€)"
          inputmode="decimal"
          placeholder="25,00"
          :error="fieldErrors.defaultPriceCents"
        />
      </div>

      <UiAlert
        v-if="formError"
        variant="error"
      >
        {{ formError }}
      </UiAlert>

      <div class="flex gap-2">
        <UiButton
          type="submit"
          :loading="submitting"
        >
          {{ editingId ? "Enregistrer" : "Ajouter" }}
        </UiButton>
        <UiButton
          v-if="editingId"
          variant="secondary"
          @click="resetForm"
        >
          Annuler
        </UiButton>
      </div>
    </form>
    <p
      v-else
      class="text-sm text-ink/60"
    >
      Lecture seule : seuls les gérants et propriétaires modifient le catalogue.
    </p>
  </section>
</template>
