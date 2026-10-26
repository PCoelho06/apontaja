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
import {
  describeCatalogLoadError,
  describeDeleteError,
  describeServiceSaveError,
} from "@/lib/catalogErrors";
import {
  centsToInput,
  eurosToCents,
  formatCents,
  formatDuration,
} from "@/lib/catalogFormat";
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
    pageError.value = describeCatalogLoadError(e, "services");
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
    errors.name = "O nome é obrigatório.";
  }
  const duration = Number(form.duration);
  if (!Number.isInteger(duration) || duration < 1 || duration > 1440) {
    errors.defaultDurationMinutes =
      "Duração: número inteiro entre 1 e 1440 minutos.";
  }
  const price = eurosToCents(form.price);
  if (price === null) {
    errors.defaultPriceCents = "Preço inválido (por exemplo, 25 ou 25,50).";
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
    const failure = describeServiceSaveError(e);
    formError.value = failure.message;
    fieldErrors.value = failure.fieldErrors;
  } finally {
    submitting.value = false;
  }
}

async function remove(service: SalonServiceItem) {
  if (!globalThis.confirm(`Eliminar «${service.name}»?`)) {
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
    pageError.value = describeDeleteError(e, "service");
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
      A carregar...
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
              {{ expandedId === service.serviceId ? "Ocultar" : "Recursos" }}
            </UiButton>
            <template v-if="canManage">
              <UiButton
                variant="secondary"
                size="sm"
                @click="startEdit(service)"
              >
                Editar
              </UiButton>
              <UiButton
                variant="danger"
                size="sm"
                @click="remove(service)"
              >
                Eliminar
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
      Ainda não há serviços.
    </p>

    <form
      v-if="canManage"
      class="space-y-4 rounded-md border border-border bg-white p-4"
      @submit.prevent="submit"
    >
      <h2 class="text-lg font-medium text-ink">
        {{ editingId ? "Editar o serviço" : "Novo serviço" }}
      </h2>

      <UiInput
        v-model="form.name"
        label="Nome"
        maxlength="100"
        :error="fieldErrors.name"
      />

      <UiTextarea
        v-model="form.description"
        label="Descrição (opcional)"
        maxlength="1000"
        rows="2"
        :error="fieldErrors.description"
      />

      <div class="grid grid-cols-2 gap-4">
        <UiInput
          v-model="form.duration"
          label="Duração (minutos)"
          inputmode="numeric"
          :error="fieldErrors.defaultDurationMinutes"
        />
        <UiInput
          v-model="form.price"
          label="Preço (€)"
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
          {{ editingId ? "Guardar" : "Adicionar" }}
        </UiButton>
        <UiButton
          v-if="editingId"
          variant="secondary"
          @click="resetForm"
        >
          Cancelar
        </UiButton>
      </div>
    </form>
    <p
      v-else
      class="text-sm text-ink/60"
    >
      Só leitura: apenas gestores e proprietários podem alterar o catálogo.
    </p>
  </section>
</template>
