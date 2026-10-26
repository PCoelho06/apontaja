<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";

import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

import { useCatalogAccess } from "@/composables/useCatalogAccess";
import {
  parseClosureConflict,
  type ClosureConflict,
} from "@/lib/closureConflict";
import {
  describeCatalogLoadError,
  describeClosureDeleteError,
  describeClosureSaveError,
} from "@/lib/catalogErrors";
import {
  formatInstant,
  instantToZonedLocal,
  isValidTimeZone,
  zonedLocalToInstant,
} from "@/lib/zonedTime";
import {
  useClosureStore,
  type ClosurePayload,
  type SalonClosure,
} from "@/stores/closure";
import { useResourceStore } from "@/stores/resource";
import { useSalonStore } from "@/stores/salon";

const route = useRoute();
const store = useClosureStore();
const resourceStore = useResourceStore();
const salonStore = useSalonStore();
const salonId = computed(() => String(route.params.salonId));
const { canManage } = useCatalogAccess(() => salonId.value);

const timeZone = computed(() => salonStore.currentSalon?.timezone ?? "");
const timeZoneValid = computed(() => isValidTimeZone(timeZone.value));

const scope = ref("salon");
const scopeResourceId = computed(() =>
  scope.value === "salon" ? null : scope.value,
);
const showPast = ref(false);
const loading = ref(true);
const pageError = ref<string | null>(null);
const editingId = ref<string | null>(null);
const form = reactive({ start: "", end: "", reason: "" });
const formError = ref<string | null>(null);
const conflict = ref<ClosureConflict | null>(null);
const submitting = ref(false);

const sortedClosures = computed(() =>
  [...store.closures].sort((a, b) => a.startAt.localeCompare(b.startAt)),
);

function display(iso: string): string {
  return timeZoneValid.value ? formatInstant(iso, timeZone.value) : iso;
}

function resourceName(resourceId: string): string {
  return (
    resourceStore.resources.find((r) => r.resourceId === resourceId)?.name ??
    resourceId
  );
}

async function load() {
  loading.value = true;
  pageError.value = null;
  try {
    await store.fetchClosures(
      salonId.value,
      scopeResourceId.value,
      showPast.value ? undefined : new Date().toISOString(),
    );
  } catch (e) {
    pageError.value = describeCatalogLoadError(e, "closures");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  editingId.value = null;
  form.start = "";
  form.end = "";
  form.reason = "";
  formError.value = null;
  conflict.value = null;
}

watch(
  salonId,
  async (id) => {
    scope.value = "salon";
    resetForm();
    try {
      await resourceStore.fetchResources(id);
    } catch (e) {
      pageError.value = describeCatalogLoadError(e, "resources");
    }
  },
  { immediate: true },
);

watch([salonId, scope, showPast], load, { immediate: true });

function startEdit(closure: SalonClosure) {
  editingId.value = closure.closureId;
  form.start = instantToZonedLocal(closure.startAt, timeZone.value);
  form.end = instantToZonedLocal(closure.endAt, timeZone.value);
  form.reason = closure.reason ?? "";
  formError.value = null;
  conflict.value = null;
}

function buildPayload(): ClosurePayload | null {
  try {
    const startAt = zonedLocalToInstant(form.start, timeZone.value);
    const endAt = zonedLocalToInstant(form.end, timeZone.value);
    if (endAt <= startAt) {
      formError.value = "O fim deve ser depois do início.";
      return null;
    }
    return { startAt, endAt, reason: form.reason.trim() || null };
  } catch (e) {
    formError.value = e instanceof Error ? e.message : "Datas inválidas.";
    return null;
  }
}

async function submit() {
  formError.value = null;
  conflict.value = null;
  const payload = buildPayload();
  if (!payload) {
    return;
  }
  submitting.value = true;
  try {
    if (editingId.value) {
      await store.updateClosure(
        salonId.value,
        scopeResourceId.value,
        editingId.value,
        payload,
      );
    } else {
      await store.createClosure(salonId.value, scopeResourceId.value, payload);
    }
    resetForm();
    await load();
  } catch (e) {
    conflict.value = parseClosureConflict(e);
    formError.value = conflict.value ? null : describeClosureSaveError(e);
  } finally {
    submitting.value = false;
  }
}

async function remove(closure: SalonClosure) {
  if (!globalThis.confirm("Eliminar este encerramento?")) {
    return;
  }
  pageError.value = null;
  try {
    await store.removeClosure(
      salonId.value,
      scopeResourceId.value,
      closure.closureId,
    );
    if (editingId.value === closure.closureId) {
      resetForm();
    }
    await load();
  } catch (e) {
    pageError.value = describeClosureDeleteError(e);
  }
}
</script>

<template>
  <section class="space-y-6">
    <UiAlert
      v-if="!timeZoneValid"
      variant="error"
    >
      O fuso horário do salão («{{ timeZone }}») não é um identificador válido
      (por exemplo, Europe/Lisbon): o registo de encerramentos está desativado.
    </UiAlert>

    <div class="space-y-3">
      <div class="w-72">
        <UiSelect
          v-model="scope"
          label="Encerramentos de"
          :disabled="editingId !== null"
        >
          <option value="salon">
            Todo o salão
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
      <UiCheckbox
        v-model="showPast"
        label="Mostrar também os encerramentos passados"
      />
      <p class="text-sm text-ink/60">
        Horas do salão ({{ timeZone }}). Um encerramento do salão bloqueia todos
        os recursos; um encerramento de um recurso bloqueia apenas esse recurso.
      </p>
    </div>

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
      v-else-if="sortedClosures.length > 0"
      class="divide-y divide-border rounded-md border border-border bg-white"
    >
      <li
        v-for="closure in sortedClosures"
        :key="closure.closureId"
        class="flex items-center justify-between gap-4 px-4 py-3"
      >
        <div>
          <p class="font-medium text-ink">
            {{ display(closure.startAt) }} → {{ display(closure.endAt) }}
          </p>
          <p
            v-if="closure.reason"
            class="text-sm text-ink/60"
          >
            {{ closure.reason }}
          </p>
        </div>
        <div
          v-if="canManage && timeZoneValid"
          class="flex gap-2"
        >
          <UiButton
            variant="secondary"
            size="sm"
            @click="startEdit(closure)"
          >
            Editar
          </UiButton>
          <UiButton
            variant="danger"
            size="sm"
            @click="remove(closure)"
          >
            Eliminar
          </UiButton>
        </div>
      </li>
    </ul>
    <p
      v-else
      class="text-ink/60"
    >
      Sem encerramentos.
    </p>

    <form
      v-if="canManage && timeZoneValid"
      class="space-y-4 rounded-md border border-border bg-white p-4"
      @submit.prevent="submit"
    >
      <h2 class="text-lg font-medium text-ink">
        {{ editingId ? "Editar o encerramento" : "Novo encerramento" }}
      </h2>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <UiInput
          v-model="form.start"
          type="datetime-local"
          label="Início"
          required
        />
        <UiInput
          v-model="form.end"
          type="datetime-local"
          label="Fim"
          required
        />
      </div>

      <UiInput
        v-model="form.reason"
        label="Motivo (opcional)"
        maxlength="255"
      />

      <UiAlert
        v-if="formError"
        variant="error"
      >
        {{ formError }}
      </UiAlert>

      <UiAlert
        v-if="conflict"
        variant="error"
      >
        <p class="font-medium">
          Encerramento impossível: {{ conflict.count }}
          {{ conflict.count === 1 ? "marcação ativa sobrepõe-se" : "marcações ativas sobrepõem-se" }}
          a este período. Cancele-as ou mude-as de hora primeiro.
        </p>
        <ul class="mt-1 list-disc pl-5">
          <li
            v-for="a in conflict.appointments"
            :key="a.appointmentId"
          >
            {{ display(a.startAt) }} → {{ display(a.endAt) }} ·
            {{ resourceName(a.resourceId) }}
          </li>
        </ul>
        <p
          v-if="conflict.count > conflict.appointments.length"
          class="mt-1"
        >
          … e mais {{ conflict.count - conflict.appointments.length }}.
        </p>
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
      v-else-if="!canManage"
      class="text-sm text-ink/60"
    >
      Só leitura: apenas gestores e proprietários podem alterar os
      encerramentos.
    </p>
  </section>
</template>
