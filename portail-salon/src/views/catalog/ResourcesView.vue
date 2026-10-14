<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiButton, UiInput, UiSelect } from "@apontaja/ui-kit";

import { useCatalogAccess } from "@/composables/useCatalogAccess";
import { ApiError } from "@/lib/apiClient";
import { errorMessage } from "@/lib/errorMessage";
import {
  useResourceStore,
  type ResourceType,
  type SalonResource,
} from "@/stores/resource";
import { useStaffStore, type StaffMember } from "@/stores/staff";

const TYPE_LABELS: Record<ResourceType, string> = {
  EMPLOYEE: "Collaborateur",
  MACHINE: "Machine",
};

const route = useRoute();
const store = useResourceStore();
const staffStore = useStaffStore();
const salonId = computed(() => String(route.params.salonId));
const { canManage } = useCatalogAccess(() => salonId.value);

const loading = ref(true);
const pageError = ref<string | null>(null);
const editingId = ref<string | null>(null);
const form = reactive({
  name: "",
  type: "EMPLOYEE" as ResourceType,
  staffMembershipId: "", // "" = aucun lien
});
const formError = ref<string | null>(null);
const fieldErrors = ref<Record<string, string>>({});
const submitting = ref(false);

async function load() {
  loading.value = true;
  pageError.value = null;
  try {
    await Promise.all([
      store.fetchResources(salonId.value),
      staffStore.fetchMembers(salonId.value),
    ]);
  } catch (e) {
    pageError.value = errorMessage(e, "Impossible de charger les ressources.");
  } finally {
    loading.value = false;
  }
}

watch(salonId, load, { immediate: true });

function memberName(member: StaffMember): string {
  return member.email ?? member.accountId.slice(0, 8);
}

function memberLabel(staffMembershipId: string): string {
  const member = staffStore.members.find(
    (m) => m.staffMembershipId === staffMembershipId,
  );
  return member ? memberName(member) : "membre retiré";
}

/** Membres encore libres (celui de la ressource en cours d'édition reste proposé). */
const selectableMembers = computed(() => {
  const taken = new Set(
    store.resources.flatMap((r) =>
      r.staffMembershipId && r.resourceId !== editingId.value
        ? [r.staffMembershipId]
        : [],
    ),
  );
  return staffStore.members.filter((m) => !taken.has(m.staffMembershipId));
});

// Seul un collaborateur peut être lié : on oublie le choix si on passe à Machine.
watch(
  () => form.type,
  (type) => {
    if (type !== "EMPLOYEE") {
      form.staffMembershipId = "";
    }
  },
);

function resetForm() {
  editingId.value = null;
  form.name = "";
  form.type = "EMPLOYEE";
  form.staffMembershipId = "";
  formError.value = null;
  fieldErrors.value = {};
}

function startEdit(resource: SalonResource) {
  editingId.value = resource.resourceId;
  form.name = resource.name;
  form.type = resource.type;
  // Lien vers un membre retiré : on le laisse vide pour ne pas renvoyer un id périmé (404).
  form.staffMembershipId = staffStore.members.some(
    (m) => m.staffMembershipId === resource.staffMembershipId,
  )
    ? (resource.staffMembershipId ?? "")
    : "";
  formError.value = null;
  fieldErrors.value = {};
}

async function submit() {
  submitting.value = true;
  formError.value = null;
  fieldErrors.value = {};
  try {
    const payload = {
      name: form.name.trim(),
      type: form.type,
      staffMembershipId:
        form.type === "EMPLOYEE" && form.staffMembershipId
          ? form.staffMembershipId
          : null,
    };
    if (editingId.value) {
      await store.updateResource(salonId.value, editingId.value, payload);
    } else {
      await store.createResource(salonId.value, payload);
    }
    resetForm();
  } catch (e) {
    formError.value = errorMessage(e, "Enregistrement impossible.");
    fieldErrors.value = e instanceof ApiError ? (e.fieldErrors ?? {}) : {};
  } finally {
    submitting.value = false;
  }
}

async function remove(resource: SalonResource) {
  if (!globalThis.confirm(`Supprimer « ${resource.name} » ?`)) {
    return;
  }
  pageError.value = null;
  try {
    await store.removeResource(salonId.value, resource.resourceId);
    if (editingId.value === resource.resourceId) {
      resetForm();
    }
  } catch (e) {
    pageError.value = errorMessage(e, "Suppression impossible.");
  }
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
      v-else-if="store.resources.length > 0"
      class="divide-y divide-border rounded-md border border-border bg-white"
    >
      <li
        v-for="resource in store.resources"
        :key="resource.resourceId"
        class="flex items-center justify-between gap-4 px-4 py-3"
      >
        <div>
          <p class="font-medium text-ink">
            {{ resource.name }}
          </p>
          <p class="text-sm text-ink/60">
            {{ TYPE_LABELS[resource.type] }}
            <template v-if="resource.staffMembershipId">
              · lié à {{ memberLabel(resource.staffMembershipId) }}
            </template>
          </p>
        </div>
        <div
          v-if="canManage"
          class="flex gap-2"
        >
          <UiButton
            variant="secondary"
            size="sm"
            @click="startEdit(resource)"
          >
            Modifier
          </UiButton>
          <UiButton
            variant="danger"
            size="sm"
            @click="remove(resource)"
          >
            Supprimer
          </UiButton>
        </div>
      </li>
    </ul>
    <div
      v-else
      class="space-y-1 text-ink/60"
    >
      <p>Aucune ressource pour le moment.</p>
      <p class="text-sm">
        Une ressource est ce qui se réserve dans l'agenda : un collaborateur ou
        une machine. L'équipe du salon (comptes et rôles) se gère séparément ;
        liez un collaborateur à un membre de l'équipe si vous le souhaitez.
      </p>
    </div>

    <form
      v-if="canManage"
      class="space-y-4 rounded-md border border-border bg-white p-4"
      @submit.prevent="submit"
    >
      <h2 class="text-lg font-medium text-ink">
        {{ editingId ? "Modifier la ressource" : "Nouvelle ressource" }}
      </h2>

      <UiInput
        v-model="form.name"
        label="Nom"
        maxlength="100"
        required
        :error="fieldErrors.name"
      />

      <UiSelect
        v-model="form.type"
        label="Type"
      >
        <option value="EMPLOYEE">
          Collaborateur
        </option>
        <option value="MACHINE">
          Machine
        </option>
      </UiSelect>

      <UiSelect
        v-if="form.type === 'EMPLOYEE'"
        v-model="form.staffMembershipId"
        label="Membre de l'équipe (optionnel)"
        :hint="
          selectableMembers.length === 0
            ? 'Tous les membres de l\'équipe sont déjà liés à une ressource.'
            : undefined
        "
      >
        <option value="">
          Aucun
        </option>
        <option
          v-for="member in selectableMembers"
          :key="member.staffMembershipId"
          :value="member.staffMembershipId"
        >
          {{ memberName(member) }} · {{ member.role }}
        </option>
      </UiSelect>

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
