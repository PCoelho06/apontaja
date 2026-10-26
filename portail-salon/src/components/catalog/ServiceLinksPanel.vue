<script setup lang="ts">
import { computed, ref, watch } from "vue";

import {
  UiAlert,
  UiBadge,
  UiButton,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

import {
  centsToInput,
  eurosToCents,
  formatCents,
  formatDuration,
} from "@/lib/catalogFormat";
import {
  describeCatalogLoadError,
  describeLinkError,
} from "@/lib/catalogErrors";
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
    error.value = describeCatalogLoadError(e, "links");
  }
}

// Recharge aussi quand les valeurs par défaut changent (valeurs effectives recalculées).
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
    link.overridePriceCents === null
      ? ""
      : centsToInput(link.overridePriceCents);
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
      error.value = "Preço específico inválido (por exemplo, 25 ou 25,50).";
      return null;
    }
  }
  let duration: number | null = null;
  if (durationInput.value.trim() !== "") {
    duration = Number(durationInput.value);
    if (!Number.isInteger(duration) || duration < 1 || duration > 1440) {
      error.value = "Duração específica: número inteiro entre 1 e 1440 minutos.";
      return null;
    }
  }
  return { overridePriceCents: price, overrideDurationMinutes: duration };
}

async function save() {
  error.value = null;
  if (!selectedResourceId.value) {
    error.value = "Escolha um recurso.";
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
    error.value = describeLinkError(e, "save");
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
    error.value = describeLinkError(e, "remove");
  }
}
</script>

<template>
  <div class="space-y-3 rounded-md bg-ink/5 p-3">
    <p class="text-sm font-medium text-ink">
      Recursos que podem realizar este serviço
    </p>

    <ul
      v-if="links.length > 0"
      class="divide-y divide-border text-sm"
    >
      <li
        v-for="link in links"
        :key="link.resourceId"
        class="flex items-center justify-between gap-3 py-2"
      >
        <div class="text-ink">
          <span class="font-medium">{{ resourceName(link.resourceId) }}</span>
          <span class="text-ink/60">
            — {{ formatDuration(link.effectiveDurationMinutes) }} ·
            {{ formatCents(link.effectivePriceCents) }}
          </span>
          <UiBadge
            v-if="
              link.overridePriceCents !== null ||
                link.overrideDurationMinutes !== null
            "
            tone="brass"
            class="ml-1"
          >
            específico
          </UiBadge>
        </div>
        <div
          v-if="canManage"
          class="flex gap-2"
        >
          <UiButton
            variant="secondary"
            size="sm"
            @click="startEdit(link)"
          >
            Editar
          </UiButton>
          <UiButton
            variant="danger"
            size="sm"
            @click="remove(link)"
          >
            Retirar
          </UiButton>
        </div>
      </li>
    </ul>
    <p
      v-else
      class="text-sm text-ink/60"
    >
      Nenhum recurso associado: este serviço não pode ser reservado.
    </p>

    <form
      v-if="canManage"
      class="grid grid-cols-1 items-end gap-3 sm:grid-cols-4"
      @submit.prevent="save"
    >
      <UiSelect
        v-model="selectedResourceId"
        label="Recurso"
        :disabled="editing"
      >
        <option
          value=""
          disabled
        >
          Escolher...
        </option>
        <option
          v-for="resource in selectableResources"
          :key="resource.resourceId"
          :value="resource.resourceId"
        >
          {{ resource.name }}
        </option>
      </UiSelect>
      <UiInput
        v-model="priceInput"
        label="Preço (específico)"
        inputmode="decimal"
        :placeholder="`Padrão ${centsToInput(service.defaultPriceCents)}`"
      />
      <UiInput
        v-model="durationInput"
        label="Duração (específica, min)"
        inputmode="numeric"
        :placeholder="`Padrão ${service.defaultDurationMinutes}`"
      />
      <div class="flex gap-2">
        <UiButton
          type="submit"
          :loading="submitting"
        >
          {{ editing ? "Guardar" : "Associar" }}
        </UiButton>
        <UiButton
          v-if="editing"
          variant="secondary"
          @click="resetForm"
        >
          Cancelar
        </UiButton>
      </div>
    </form>
    <p
      v-if="canManage && !editing && selectableResources.length === 0"
      class="text-sm text-ink/60"
    >
      Não há recursos para associar: crie um no separador Recursos, ou todos
      já estão associados a este serviço.
    </p>
    <UiAlert
      v-if="error"
      variant="error"
    >
      {{ error }}
    </UiAlert>
  </div>
</template>
