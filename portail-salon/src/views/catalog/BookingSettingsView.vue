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

import { errorMessage } from "@apontaja/web-core/error-message";
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
    pageError.value = errorMessage(
      e,
      "Impossible de charger les réglages de réservation.",
    );
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
    formError.value = "Corrigez les champs signalés.";
    return;
  }
  saving.value = true;
  try {
    draft.value = toDraft(await store.saveSettings(salonId.value, parsed.settings));
    notice.value = "Réglages enregistrés.";
  } catch (e) {
    formError.value = errorMessage(e, "L'enregistrement a échoué.");
  } finally {
    saving.value = false;
  }
}

async function copyLink() {
  try {
    await globalThis.navigator.clipboard.writeText(publicUrl.value);
    copyNotice.value = "Lien copié.";
  } catch {
    copyNotice.value = "Copie impossible : sélectionnez le lien et copiez-le.";
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h2 class="font-display text-xl text-ink">
        Réservation en ligne
      </h2>
      <p class="mt-1 text-sm text-ink/70">
        Les clients réservent depuis leur compte, avec un email vérifié. Par
        défaut, vous validez chaque demande vous-même.
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
      Chargement…
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
        Seuls les gérants et propriétaires peuvent modifier ces réglages.
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
            label="Activer la réservation en ligne"
            hint="Désactivée, la page publique du salon répond « introuvable »."
          />
          <UiCheckbox
            v-model="draft.autoConfirm"
            label="Confirmer automatiquement les réservations"
            hint="Sinon, chaque demande attend votre validation (écran Demandes)."
          />

          <UiSelect
            v-model="draft.slotGranularityMinutes"
            label="Pas des créneaux proposés"
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
            label="Préavis minimal (minutes)"
            inputmode="numeric"
            :hint="`Entre ${LIMITS.minNoticeMinutes.min} et ${LIMITS.minNoticeMinutes.max}.`"
            :error="errors.minNoticeMinutes"
          />
          <UiInput
            v-model="draft.horizonDays"
            label="Réservable jusqu'à (jours à l'avance)"
            inputmode="numeric"
            :hint="`Entre ${LIMITS.horizonDays.min} et ${LIMITS.horizonDays.max}.`"
            :error="errors.horizonDays"
          />
          <UiInput
            v-model="draft.cancellationDeadlineHours"
            label="Annulation par le client jusqu'à (heures avant)"
            inputmode="numeric"
            :hint="`Entre ${LIMITS.cancellationDeadlineHours.min} et ${LIMITS.cancellationDeadlineHours.max} ; 0 = jusqu'au début. Concerne les rendez-vous confirmés.`"
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
          Enregistrer
        </UiButton>
      </form>

      <section class="space-y-2 rounded-md border border-border bg-white p-4">
        <h3 class="text-sm font-medium text-ink">
          Lien public du salon
        </h3>
        <p class="break-all text-sm text-ink/80">
          {{ publicUrl }}
        </p>
        <p class="text-xs text-ink/60">
          Ce lien sera actif avec la réservation côté client (prochaine
          étape).
        </p>
        <div class="flex items-center gap-3">
          <UiButton
            variant="secondary"
            size="sm"
            @click="copyLink"
          >
            Copier
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
