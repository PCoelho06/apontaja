<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiButton, UiDialog } from "@apontaja/ui-kit";

import { ApiError } from "@/lib/apiClient";
import { visitLabel } from "@/lib/customerFormat";
import {
  absorbedIds,
  memberContact,
  memberName,
  mergeableOthers,
  notMergeableLabel,
  reasonsLabel,
  type DuplicateGroup,
  type DuplicateMember,
} from "@/lib/customerDuplicates";
import { errorMessage } from "@/lib/errorMessage";
import { useCustomerStore } from "@/stores/customer";
import { useSalonStore } from "@/stores/salon";

const route = useRoute();
const salonId = computed(() => String(route.params.salonId));

const salonStore = useSalonStore();
const customerStore = useCustomerStore();

const loading = ref(true);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const groups = ref<DuplicateGroup[]>([]);
const truncated = ref(false);

const timeZone = computed(() => salonStore.currentSalon?.timezone ?? "");

const dialogOpen = ref(false);
const pendingGroup = ref<DuplicateGroup | null>(null);
const pendingSurvivor = ref<DuplicateMember | null>(null);
const excluded = ref<string[]>([]);
const merging = ref(false);
const dialogError = ref<string | null>(null);

const candidates = computed(() =>
  pendingGroup.value && pendingSurvivor.value
    ? mergeableOthers(pendingGroup.value, pendingSurvivor.value.customerProfileId)
    : [],
);
const selectedIds = computed(() =>
  pendingGroup.value && pendingSurvivor.value
    ? absorbedIds(
        pendingGroup.value,
        pendingSurvivor.value.customerProfileId,
        excluded.value,
      )
    : [],
);

function failure(e: unknown, fallback: string): string {
  if (e instanceof ApiError && e.status === 403) {
    return "Seuls les gérants et propriétaires peuvent gérer les doublons.";
  }
  return errorMessage(e, fallback);
}

async function load() {
  loading.value = true;
  error.value = null;
  try {
    const report = await customerStore.fetchDuplicates(salonId.value);
    groups.value = report.groups;
    truncated.value = report.truncated;
  } catch (e) {
    error.value = failure(e, "Impossible de charger les doublons.");
  } finally {
    loading.value = false;
  }
}

watch(
  salonId,
  async (id) => {
    notice.value = null;
    try {
      await salonStore.fetchSalon(id);
    } catch (e) {
      error.value = failure(e, "Impossible de charger le salon.");
      loading.value = false;
      return;
    }
    await load();
  },
  { immediate: true },
);

function startMerge(group: DuplicateGroup, survivor: DuplicateMember) {
  pendingGroup.value = group;
  pendingSurvivor.value = survivor;
  excluded.value = [];
  dialogError.value = null;
  dialogOpen.value = true;
}

function toggle(id: string) {
  excluded.value = excluded.value.includes(id)
    ? excluded.value.filter((other) => other !== id)
    : [...excluded.value, id];
}

async function confirmMerge() {
  const survivor = pendingSurvivor.value;
  if (!survivor || selectedIds.value.length === 0) {
    return;
  }
  merging.value = true;
  dialogError.value = null;
  try {
    await customerStore.mergeCustomers(
      salonId.value,
      survivor.customerProfileId,
      selectedIds.value,
    );
    notice.value = `${selectedIds.value.length} profil(s) fusionné(s) dans ${memberName(survivor)}.`;
    dialogOpen.value = false;
    await load();
  } catch (e) {
    dialogError.value = failure(e, "La fusion a échoué.");
  } finally {
    merging.value = false;
  }
}
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div>
        <RouterLink
          :to="{ name: 'customers', params: { salonId } }"
          class="text-sm text-wine hover:underline"
        >
          ← Clients
        </RouterLink>
        <h1 class="mt-2 font-display text-2xl text-ink">
          Doublons
        </h1>
        <p class="mt-1 text-sm text-ink/70">
          Profils qui partagent un email ou un téléphone. Rien n'est fusionné
          automatiquement : vous choisissez le profil à conserver.
        </p>
      </div>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>
      <UiAlert
        v-if="notice"
        variant="success"
      >
        {{ notice }}
      </UiAlert>
      <p
        v-if="loading"
        class="text-sm text-ink/70"
      >
        Chargement…
      </p>

      <template v-else-if="!error">
        <p
          v-if="groups.length === 0"
          class="text-ink/60"
        >
          Aucun doublon détecté.
        </p>
        <UiAlert
          v-if="truncated"
          variant="error"
        >
          Seuls les premiers groupes sont affichés. Fusionnez-les puis
          rechargez la page pour voir la suite.
        </UiAlert>

        <section
          v-for="(group, index) in groups"
          :key="index"
          class="space-y-3 rounded-md border border-border bg-white p-4"
        >
          <p class="text-sm font-medium text-wine">
            {{ reasonsLabel(group.reasons) }}
          </p>
          <ul class="divide-y divide-border">
            <li
              v-for="member in group.customers"
              :key="member.customerProfileId"
              class="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div class="text-sm">
                <p class="font-medium text-ink">
                  {{ memberName(member) }}
                </p>
                <p class="text-ink/60">
                  {{ memberContact(member) || "Aucun moyen de contact" }}
                </p>
                <p class="text-xs text-ink/60">
                  Dernière visite : {{ visitLabel(member.lastVisitAt, timeZone) }}
                  · Prochain rendez-vous :
                  {{ visitLabel(member.nextVisitAt, timeZone) }}
                </p>
                <p
                  v-if="!member.mergeable"
                  class="text-xs text-ink/70"
                >
                  {{ notMergeableLabel(member.notMergeableReason) }}
                </p>
              </div>
              <UiButton
                v-if="member.mergeable"
                variant="secondary"
                size="sm"
                @click="startMerge(group, member)"
              >
                Conserver ce profil
              </UiButton>
            </li>
          </ul>
        </section>
      </template>

      <UiDialog
        v-model:open="dialogOpen"
        title="Fusionner des clients"
      >
        <div
          v-if="pendingSurvivor"
          class="space-y-4 text-sm text-ink"
        >
          <p>
            Conserver <strong>{{ memberName(pendingSurvivor) }}</strong> et lui
            rattacher les profils ci-dessous : leurs rendez-vous, leurs notes
            et leur consentement le plus récent.
          </p>
          <ul class="divide-y divide-border rounded-md border border-border">
            <li
              v-for="member in candidates"
              :key="member.customerProfileId"
              class="flex items-center justify-between gap-3 px-3 py-2"
              :class="{ 'opacity-50': excluded.includes(member.customerProfileId) }"
            >
              <span>
                <span class="block">{{ memberName(member) }}</span>
                <span class="block text-xs text-ink/60">{{
                  memberContact(member) || "Aucun moyen de contact"
                }}</span>
              </span>
              <UiButton
                variant="secondary"
                size="sm"
                :disabled="merging"
                @click="toggle(member.customerProfileId)"
              >
                {{ excluded.includes(member.customerProfileId) ? "Inclure" : "Exclure" }}
              </UiButton>
            </li>
          </ul>
          <UiAlert variant="error">
            Cette opération est irréversible : les profils fusionnés sont
            retirés du carnet et leurs données personnelles effacées.
          </UiAlert>
          <UiAlert
            v-if="dialogError"
            variant="error"
          >
            {{ dialogError }}
          </UiAlert>
          <div class="flex justify-end gap-2">
            <UiButton
              variant="secondary"
              :disabled="merging"
              @click="dialogOpen = false"
            >
              Annuler
            </UiButton>
            <UiButton
              variant="danger"
              :loading="merging"
              :disabled="selectedIds.length === 0"
              @click="confirmMerge"
            >
              Fusionner {{ selectedIds.length }} profil(s)
            </UiButton>
          </div>
        </div>
      </UiDialog>
    </div>
  </main>
</template>
