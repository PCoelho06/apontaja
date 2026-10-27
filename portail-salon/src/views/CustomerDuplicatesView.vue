<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiButton, UiDialog } from "@apontaja/ui-kit";

import {
  describeCustomerLoadError,
  describeMergeError,
  shouldReloadAfterMerge,
} from "@/lib/customerErrors";
import { NO_CONTACT_LABEL, visitLabel } from "@/lib/customerFormat";
import {
  CLAIMED_SURVIVOR_WARNING,
  absorbedIds,
  canKeep,
  memberContact,
  memberName,
  mergeableOthers,
  notMergeableLabel,
  reasonsLabel,
  type DuplicateGroup,
  type DuplicateMember,
} from "@/lib/customerDuplicates";
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
/** La liste est périmée (404/409) : la fusion ne peut plus être relancée avec ces profils. */
const stale = ref(false);
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

async function load() {
  loading.value = true;
  error.value = null;
  try {
    const report = await customerStore.fetchDuplicates(salonId.value);
    groups.value = report.groups;
    truncated.value = report.truncated;
  } catch (e) {
    error.value = describeCustomerLoadError(e, "duplicates");
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
      error.value = describeCustomerLoadError(e, "salon");
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
  stale.value = false;
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
    notice.value = `${selectedIds.value.length} perfil(is) fundido(s) em ${memberName(survivor)}.`;
    dialogOpen.value = false;
    await load();
  } catch (e) {
    dialogError.value = describeMergeError(e);
    if (shouldReloadAfterMerge(e)) {
      stale.value = true;
      await load();
    }
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
          ← Clientes
        </RouterLink>
        <h1 class="mt-2 font-display text-2xl text-ink">
          Duplicados
        </h1>
        <p class="mt-1 text-sm text-ink/70">
          Perfis que partilham um email ou um telefone. Nada é fundido
          automaticamente: escolha o perfil a manter.
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
        A carregar...
      </p>

      <template v-else-if="!error">
        <p
          v-if="groups.length === 0"
          class="text-ink/60"
        >
          Nenhum duplicado detetado.
        </p>
        <UiAlert
          v-if="truncated"
          variant="error"
        >
          Só são apresentados os primeiros grupos. Funda-os e recarregue a
          página para ver os seguintes.
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
                  {{ memberContact(member) || NO_CONTACT_LABEL }}
                </p>
                <p class="text-xs text-ink/60">
                  Última visita: {{ visitLabel(member.lastVisitAt, timeZone) }}
                  · Próxima marcação:
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
                v-if="canKeep(group, member)"
                variant="secondary"
                size="sm"
                @click="startMerge(group, member)"
              >
                Manter este perfil
              </UiButton>
            </li>
          </ul>
        </section>
      </template>

      <UiDialog
        v-model:open="dialogOpen"
        title="Fundir clientes"
        close-label="Fechar"
      >
        <div
          v-if="pendingSurvivor"
          class="space-y-4 text-sm text-ink"
        >
          <p>
            Manter <strong>{{ memberName(pendingSurvivor) }}</strong> e
            associar-lhe os perfis abaixo: as respetivas marcações, notas e o
            consentimento mais recente.
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
                  memberContact(member) || NO_CONTACT_LABEL
                }}</span>
              </span>
              <UiButton
                variant="secondary"
                size="sm"
                :disabled="merging"
                @click="toggle(member.customerProfileId)"
              >
                {{ excluded.includes(member.customerProfileId) ? "Incluir" : "Excluir" }}
              </UiButton>
            </li>
          </ul>
          <UiAlert
            v-if="pendingSurvivor.claimed"
            variant="info"
          >
            {{ CLAIMED_SURVIVOR_WARNING }}
          </UiAlert>
          <UiAlert variant="error">
            Esta operação é irreversível: os perfis fundidos são retirados do
            ficheiro de clientes e os seus dados pessoais apagados.
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
              Cancelar
            </UiButton>
            <UiButton
              variant="danger"
              :loading="merging"
              :disabled="selectedIds.length === 0 || stale"
              @click="confirmMerge"
            >
              Fundir {{ selectedIds.length }} perfil(is)
            </UiButton>
          </div>
        </div>
      </UiDialog>
    </div>
  </main>
</template>



