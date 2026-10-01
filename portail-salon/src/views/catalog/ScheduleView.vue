<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { useCatalogAccess } from "@/composables/useCatalogAccess";
import { errorMessage } from "@/lib/errorMessage";
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
    <div class="space-y-2">
      <label class="block text-sm">
        Horaires de
        <select
          v-model="scope"
          :disabled="dirty"
          class="ml-2 rounded border px-2 py-1"
        >
          <option value="salon">Tout le salon</option>
          <option
            v-for="resource in resourceStore.resources"
            :key="resource.resourceId"
            :value="resource.resourceId"
          >
            {{ resource.name }}
          </option>
        </select>
      </label>
      <p
        v-if="dirty"
        class="text-xs text-amber-700"
      >
        Modifications non enregistrées : enregistrez ou annulez avant de changer
        de périmètre.
      </p>
      <p
        v-if="scope === 'salon'"
        class="text-sm text-gray-500"
      >
        Heures locales du salon. Sans aucune plage, le salon n'est jamais ouvert
        (aucune réservation possible).
      </p>
      <p
        v-else
        class="text-sm text-gray-500"
      >
        Sans aucune plage, la ressource suit les horaires du salon. Avec des
        plages, elle n'est disponible que sur celles-ci (bornées par les horaires
        du salon) et fermée les jours non listés.
      </p>
    </div>

    <p
      v-if="loading"
      class="text-gray-500"
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
        class="flex flex-col gap-2 rounded-md border p-3 sm:flex-row sm:items-start"
      >
        <p class="w-28 shrink-0 font-medium">
          {{ day.label }}
        </p>
        <div class="flex-1 space-y-2">
          <p
            v-if="week[day.code].length === 0"
            class="text-sm text-gray-500"
          >
            Aucune plage
          </p>
          <div
            v-for="(range, index) in week[day.code]"
            :key="index"
            class="flex items-center gap-2"
          >
            <input
              v-model="range.start"
              type="time"
              :disabled="!canManage"
              :aria-label="`${day.label}, début de la plage ${index + 1}`"
              class="rounded border px-2 py-1"
            >
            <span>–</span>
            <input
              v-model="range.end"
              type="time"
              :disabled="!canManage"
              :aria-label="`${day.label}, fin de la plage ${index + 1}`"
              class="rounded border px-2 py-1"
            >
            <button
              v-if="canManage"
              type="button"
              class="text-sm text-red-600 underline"
              @click="removeRange(day.code, index)"
            >
              Retirer
            </button>
          </div>
          <button
            v-if="canManage"
            type="button"
            class="text-sm underline"
            @click="addRange(day.code)"
          >
            Ajouter une plage
          </button>
        </div>
      </div>

      <p
        v-if="error"
        role="alert"
        class="text-sm text-red-600"
      >
        {{ error }}
      </p>
      <p
        v-if="saved"
        class="text-sm text-green-700"
      >
        Horaires enregistrés.
      </p>

      <div
        v-if="canManage"
        class="flex flex-wrap gap-2"
      >
        <button
          type="button"
          :disabled="!dirty || saving"
          class="rounded bg-gray-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
          @click="save"
        >
          Enregistrer
        </button>
        <button
          type="button"
          :disabled="!dirty || saving"
          class="rounded border px-3 py-1.5 text-sm disabled:opacity-50"
          @click="load"
        >
          Annuler les modifications
        </button>
        <button
          type="button"
          :disabled="saving"
          class="rounded border px-3 py-1.5 text-sm"
          @click="clearWeek"
        >
          {{
            scope === "salon"
              ? "Fermer toute la semaine"
              : "Suivre les horaires du salon"
          }}
        </button>
      </div>
      <p
        v-else
        class="text-sm text-gray-500"
      >
        Lecture seule : seuls les gérants et propriétaires modifient les horaires.
      </p>
    </div>
  </section>
</template>
