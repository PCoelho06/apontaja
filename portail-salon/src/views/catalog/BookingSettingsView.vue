<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

import {
  describeCatalogLoadError,
  describeSettingsSaveError,
} from "@/lib/catalogErrors";
import {
  GRANULARITY_OPTIONS,
  LIMITS,
  clientPortalBaseUrl,
  parseDraft,
  publicSalonUrl,
  readinessWarnings,
  toDraft,
  type DraftErrors,
  type SettingsDraft,
} from "@/lib/bookingSettings";
import { useCatalogAccess } from "@/composables/useCatalogAccess";
import { useBookingSettingsStore } from "@/stores/bookingSettings";

const route = useRoute();
const salonId = computed(() => String(route.params.salonId));
const { canManage } = useCatalogAccess(() => salonId.value);
const store = useBookingSettingsStore();

const loading = ref(true);
const saving = ref(false);
const pageError = ref<string | null>(null);
const formError = ref<string | null>(null);
const notice = ref<string | null>(null);
const copyNotice = ref<string | null>(null);
const draft = ref<SettingsDraft | null>(null);
const errors = ref<DraftErrors>({});
const warnings = ref<string[]>([]);

const publicUrl = computed(() =>
  publicSalonUrl(clientPortalBaseUrl(), salonId.value),
);

async function load(id: string) {
  loading.value = true;
  pageError.value = null;
  notice.value = null;
  draft.value = null;
  warnings.value = [];
  try {
    draft.value = toDraft(await store.fetchSettings(id));
  } catch (e) {
    pageError.value = describeCatalogLoadError(e, "settings");
    loading.value = false;
    return;
  }
  loading.value = false;
  try {
    warnings.value = readinessWarnings(await store.fetchReadiness(id));
  } catch {
    // Les avertissements sont facultatifs : leur échec ne bloque pas le formulaire.
    warnings.value = [];
  }
}

watch(salonId, load, { immediate: true });

async function submit() {
  if (!draft.value || !canManage.value) {
    return;
  }
  formError.value = null;
  notice.value = null;
  const parsed = parseDraft(draft.value);
  errors.value = parsed.errors;
  if (!parsed.settings) {
    formError.value = "Corrija os campos assinalados.";
    return;
  }
  saving.value = true;
  try {
    draft.value = toDraft(await store.saveSettings(salonId.value, parsed.settings));
    notice.value = "Definições guardadas.";
  } catch (e) {
    formError.value = describeSettingsSaveError(e);
  } finally {
    saving.value = false;
  }
}

async function copyLink() {
  try {
    await globalThis.navigator.clipboard.writeText(publicUrl.value);
    copyNotice.value = "Ligação copiada.";
  } catch {
    copyNotice.value = "Não foi possível copiar: selecione a ligação e copie-a.";
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h2 class="font-display text-xl text-ink">
        Reserva online
      </h2>
      <p class="mt-1 text-sm text-ink/70">
        Os clientes reservam a partir da sua conta, com um email verificado.
        Por defeito, é você que valida cada pedido.
      </p>
    </div>

    <UiAlert
      v-if="pageError"
      variant="error"
    >
      {{ pageError }}
    </UiAlert>
    <p
      v-else-if="loading"
      class="text-sm text-ink/70"
    >
      A carregar...
    </p>

    <template v-else-if="draft">
      <UiAlert
        v-for="warning in warnings"
        :key="warning"
        variant="info"
      >
        {{ warning }}
      </UiAlert>
      <UiAlert
        v-if="!canManage"
        variant="info"
      >
        Apenas gestores e proprietários podem alterar estas definições.
      </UiAlert>

      <form
        class="space-y-5 rounded-md border border-border bg-white p-4"
        @submit.prevent="submit"
      >
        <fieldset
          :disabled="!canManage"
          class="m-0 min-w-0 space-y-5 border-0 p-0"
        >
          <UiCheckbox
            v-model="draft.onlineBookingEnabled"
            label="Ativar a reserva online"
            hint="Desativada, a página pública do salão responde «não encontrado»."
          />
          <UiCheckbox
            v-model="draft.autoConfirm"
            label="Confirmar automaticamente as marcações"
            hint="Caso contrário, cada pedido aguarda a sua validação (ecrã Pedidos)."
          />

          <UiSelect
            v-model="draft.slotGranularityMinutes"
            label="Intervalo entre os horários propostos"
          >
            <option
              v-for="minutes in GRANULARITY_OPTIONS"
              :key="minutes"
              :value="String(minutes)"
            >
              {{ minutes }} min
            </option>
          </UiSelect>

          <UiInput
            v-model="draft.minNoticeMinutes"
            label="Antecedência mínima (minutos)"
            inputmode="numeric"
            :hint="`Entre ${LIMITS.minNoticeMinutes.min} e ${LIMITS.minNoticeMinutes.max}.`"
            :error="errors.minNoticeMinutes"
          />
          <UiInput
            v-model="draft.horizonDays"
            label="Reservável até (dias de antecedência)"
            inputmode="numeric"
            :hint="`Entre ${LIMITS.horizonDays.min} e ${LIMITS.horizonDays.max}.`"
            :error="errors.horizonDays"
          />
          <UiInput
            v-model="draft.cancellationDeadlineHours"
            label="Cancelamento pelo cliente até (horas antes)"
            inputmode="numeric"
            :hint="`Entre ${LIMITS.cancellationDeadlineHours.min} e ${LIMITS.cancellationDeadlineHours.max}; 0 = até ao início. Aplica-se às marcações confirmadas.`"
            :error="errors.cancellationDeadlineHours"
          />
        </fieldset>

        <UiAlert
          v-if="formError"
          variant="error"
        >
          {{ formError }}
        </UiAlert>
        <UiAlert
          v-if="notice"
          variant="success"
        >
          {{ notice }}
        </UiAlert>

        <UiButton
          v-if="canManage"
          type="submit"
          :loading="saving"
        >
          Guardar
        </UiButton>
      </form>

      <section class="space-y-2 rounded-md border border-border bg-white p-4">
        <h3 class="text-sm font-medium text-ink">
          Ligação pública do salão
        </h3>
        <p class="break-all text-sm text-ink/80">
          {{ publicUrl }}
        </p>
        <p class="text-xs text-ink/60">
          Partilhe esta ligação com os seus clientes. Só funciona quando a
          reserva online está ativada.
        </p>
        <div class="flex items-center gap-3">
          <UiButton
            variant="secondary"
            size="sm"
            @click="copyLink"
          >
            Copiar
          </UiButton>
          <span
            v-if="copyNotice"
            class="text-xs text-ink/70"
          >{{ copyNotice }}</span>
        </div>
      </section>
    </template>
  </section>
</template>
