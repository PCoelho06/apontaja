<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiButton, UiSelect } from "@apontaja/ui-kit";

import { useCatalogAccess } from "@/composables/useCatalogAccess";
import { errorMessage } from "@apontaja/web-core/error-message";
import {
  DAYS,
  emptyWeek,
  slotsToWeek,
  validateWeek,
  weekToSlots,
  type DayCode,
  type WeekDays,
} from "@/lib/schedule";
import { useResourceStore } from "@/stores/resource";
import { useScheduleStore } from "@/stores/schedule";

// Champs d'heure compacts (le libellé est porté par aria-label) : mêmes tokens que le ui-kit.
const TIME_INPUT_CLASS =
  "rounded-md border border-border bg-white px-2 py-1 text-sm text-ink " +
  "focus:border-wine focus:outline-none focus:ring-1 focus:ring-wine " +
  "disabled:cursor-not-allowed disabled:opacity-50";

const route = useRoute();
const store = useScheduleStore();
const resourceStore = useResourceStore();
const salonId = computed(() => String(route.params.salonId));
const { canManage } = useCatalogAccess(() => salonId.value);

const scope = ref("salon");
const resourceId = computed(() => (scope.value === "salon" ? null : scope.value));
const week = ref<WeekDays>(emptyWeek());
const original = ref("[]");
const loading = ref(true);
const saving = ref(false);
const error = ref<string | null>(null);
const saved = ref(false);
let loadCounter = 0;

const dirty = computed(
  () => JSON.stringify(weekToSlots(week.value)) !== original.value,
);

function applySlots(slots: ReturnType<typeof weekToSlots>) {
  week.value = slotsToWeek(slots);
  original.value = JSON.stringify(weekToSlots(week.value));
}

async function load() {
  const current = ++loadCounter;
  loading.value = true;
  error.value = null;
  saved.value = false;
  try {
    const slots = await store.fetchSchedule(salonId.value, resourceId.value);
    if (current === loadCounter) {
      applySlots(slots);
    }
  } catch (e) {
    if (current === loadCounter) {
      error.value = errorMessage(e, "Impossible de charger les horaires.");
    }
  } finally {
    if (current === loadCounter) {
      loading.value = false;
    }
  }
}

watch(
  salonId,
  async (id) => {
    scope.value = "salon";
    try {
      await resourceStore.fetchResources(id);
    } catch (e) {
      error.value = errorMessage(e, "Impossible de charger les ressources.");
    }
  },
  { immediate: true },
);

watch([salonId, scope], load, { immediate: true });

function addRange(code: DayCode) {
  week.value[code].push({ start: "09:00", end: "18:00" });
  saved.value = false;
}

function removeRange(code: DayCode, index: number) {
  week.value[code].splice(index, 1);
  saved.value = false;
}

function clearWeek() {
  week.value = emptyWeek();
  saved.value = false;
}

async function save() {
  error.value = null;
  saved.value = false;
  const problem = validateWeek(week.value);
  if (problem) {
    error.value = problem;
    return;
  }
  saving.value = true;
  try {
    applySlots(
      await store.saveSchedule(
        salonId.value,
        resourceId.value,
        weekToSlots(week.value),
      ),
    );
    saved.value = true;
  } catch (e) {
    error.value = errorMessage(e, "Enregistrement impossible.");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="space-y-6">
    <div class="space-y-3">
      <div class="w-72">
        <UiSelect
          v-model="scope"
          label="Horaires de"
          :disabled="dirty"
        >
          <option value="salon">
            Tout le salon
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
      <UiAlert v-if="dirty">
        Modifications non enregistrées : enregistrez ou annulez avant de changer
        de périmètre.
      </UiAlert>
      <p
        v-if="scope === 'salon'"
        class="text-sm text-ink/60"
      >
        Heures locales du salon. Sans aucune plage, le salon n'est jamais ouvert
        (aucune réservation possible).
      </p>
      <p
        v-else
        class="text-sm text-ink/60"
      >
        Sans aucune plage, la ressource suit les horaires du salon. Avec des
        plages, elle n'est disponible que sur celles-ci (bornées par les horaires
        du salon) et fermée les jours non listés.
      </p>
    </div>

    <p
      v-if="loading"
      class="text-sm text-ink/70"
    >
      Chargement…
    </p>

    <div
      v-else
      class="space-y-4"
    >
      <div
        v-for="day in DAYS"
        :key="day.code"
        class="flex flex-col gap-2 rounded-md border border-border bg-white p-3 sm:flex-row sm:items-start"
      >
        <p class="w-28 shrink-0 font-medium text-ink">
          {{ day.label }}
        </p>
        <div class="flex-1 space-y-2">
          <p
            v-if="week[day.code].length === 0"
            class="text-sm text-ink/60"
          >
            Aucune plage
          </p>
          <div
            v-for="(range, index) in week[day.code]"
            :key="index"
            class="flex flex-wrap items-center gap-2"
          >
            <input
              v-model="range.start"
              type="time"
              :disabled="!canManage"
              :aria-label="`${day.label}, début de la plage ${index + 1}`"
              :class="TIME_INPUT_CLASS"
            >
            <span class="text-ink/60">–</span>
            <input
              v-model="range.end"
              type="time"
              :disabled="!canManage"
              :aria-label="`${day.label}, fin de la plage ${index + 1}`"
              :class="TIME_INPUT_CLASS"
            >
            <UiButton
              v-if="canManage"
              variant="danger"
              size="sm"
              @click="removeRange(day.code, index)"
            >
              Retirer
            </UiButton>
          </div>
          <UiButton
            v-if="canManage"
            variant="secondary"
            size="sm"
            @click="addRange(day.code)"
          >
            Ajouter une plage
          </UiButton>
        </div>
      </div>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>
      <UiAlert
        v-if="saved"
        variant="success"
      >
        Horaires enregistrés.
      </UiAlert>

      <div
        v-if="canManage"
        class="flex flex-wrap gap-2"
      >
        <UiButton
          :disabled="!dirty"
          :loading="saving"
          @click="save"
        >
          Enregistrer
        </UiButton>
        <UiButton
          variant="secondary"
          :disabled="!dirty || saving"
          @click="load"
        >
          Annuler les modifications
        </UiButton>
        <UiButton
          variant="secondary"
          :disabled="saving"
          @click="clearWeek"
        >
          {{
            scope === "salon"
              ? "Fermer toute la semaine"
              : "Suivre les horaires du salon"
          }}
        </UiButton>
      </div>
      <p
        v-else
        class="text-sm text-ink/60"
      >
        Lecture seule : seuls les gérants et propriétaires modifient les horaires.
      </p>
    </div>
  </section>
</template>
