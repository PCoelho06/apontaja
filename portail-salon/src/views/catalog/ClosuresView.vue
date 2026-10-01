<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { useCatalogAccess } from "@/composables/useCatalogAccess";
import {
  parseClosureConflict,
  type ClosureConflict,
} from "@/lib/closureConflict";
import { errorMessage } from "@/lib/errorMessage";
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
    pageError.value = errorMessage(e, "Impossible de charger les fermetures.");
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
      pageError.value = errorMessage(e, "Impossible de charger les ressources.");
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
      formError.value = "La fin doit être postérieure au début.";
      return null;
    }
    return { startAt, endAt, reason: form.reason.trim() || null };
  } catch (e) {
    formError.value = e instanceof Error ? e.message : "Dates invalides.";
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
    formError.value = conflict.value
      ? null
      : errorMessage(e, "Enregistrement impossible.");
  } finally {
    submitting.value = false;
  }
}

async function remove(closure: SalonClosure) {
  if (!globalThis.confirm("Supprimer cette fermeture ?")) {
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
    pageError.value = errorMessage(e, "Suppression impossible.");
  }
}
</script>

<template>
  <section class="space-y-6">
    <p
      v-if="!timeZoneValid"
      role="alert"
      class="text-red-600"
    >
      Le fuseau horaire du salon (« {{ timeZone }} ») n'est pas un identifiant
      valide (ex. Europe/Paris) : la saisie des fermetures est désactivée.
    </p>

    <div class="space-y-2">
      <label class="block text-sm">
        Fermetures de
        <select
          v-model="scope"
          :disabled="editingId !== null"
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
      <label class="flex items-center gap-2 text-sm">
        <input
          v-model="showPast"
          type="checkbox"
        >
        Afficher aussi les fermetures passées
      </label>
      <p class="text-sm text-gray-500">
        Heures du salon ({{ timeZone }}). Une fermeture du salon bloque toutes
        les ressources ; une fermeture de ressource ne bloque que celle-ci.
      </p>
    </div>

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
      v-else-if="sortedClosures.length > 0"
      class="divide-y rounded-md border"
    >
      <li
        v-for="closure in sortedClosures"
        :key="closure.closureId"
        class="flex items-center justify-between gap-4 p-3"
      >
        <div>
          <p class="font-medium">
            {{ display(closure.startAt) }} → {{ display(closure.endAt) }}
          </p>
          <p
            v-if="closure.reason"
            class="text-sm text-gray-500"
          >
            {{ closure.reason }}
          </p>
        </div>
        <div
          v-if="canManage && timeZoneValid"
          class="flex gap-2"
        >
          <button
            type="button"
            class="text-sm underline"
            @click="startEdit(closure)"
          >
            Modifier
          </button>
          <button
            type="button"
            class="text-sm text-red-600 underline"
            @click="remove(closure)"
          >
            Supprimer
          </button>
        </div>
      </li>
    </ul>
    <p
      v-else
      class="text-gray-500"
    >
      Aucune fermeture.
    </p>

    <form
      v-if="canManage && timeZoneValid"
      class="space-y-3 rounded-md border p-4"
      @submit.prevent="submit"
    >
      <h2 class="font-semibold">
        {{ editingId ? "Modifier la fermeture" : "Nouvelle fermeture" }}
      </h2>

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label class="block text-sm">
          Début
          <input
            v-model="form.start"
            type="datetime-local"
            required
            class="mt-1 block w-full rounded border px-2 py-1"
          >
        </label>
        <label class="block text-sm">
          Fin
          <input
            v-model="form.end"
            type="datetime-local"
            required
            class="mt-1 block w-full rounded border px-2 py-1"
          >
        </label>
      </div>

      <label class="block text-sm">
        Motif (optionnel)
        <input
          v-model="form.reason"
          type="text"
          maxlength="255"
          class="mt-1 block w-full rounded border px-2 py-1"
        >
      </label>

      <p
        v-if="formError"
        role="alert"
        class="text-sm text-red-600"
      >
        {{ formError }}
      </p>

      <div
        v-if="conflict"
        role="alert"
        class="space-y-2 rounded-md border border-red-300 bg-red-50 p-3 text-sm"
      >
        <p class="font-medium text-red-700">
          Fermeture impossible : {{ conflict.count }} rendez-vous actif(s)
          recouvrent cette période. Annulez-les ou déplacez-les d'abord.
        </p>
        <ul class="list-disc pl-5">
          <li
            v-for="a in conflict.appointments"
            :key="a.appointmentId"
          >
            {{ display(a.startAt) }} → {{ display(a.endAt) }} ·
            {{ resourceName(a.resourceId) }}
          </li>
        </ul>
        <p v-if="conflict.count > conflict.appointments.length">
          … et {{ conflict.count - conflict.appointments.length }} autre(s).
        </p>
      </div>

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
      v-else-if="!canManage"
      class="text-sm text-gray-500"
    >
      Lecture seule : seuls les gérants et propriétaires modifient les
      fermetures.
    </p>
  </section>
</template>
