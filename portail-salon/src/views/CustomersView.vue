<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

import CustomerDetailDialog from "@/components/customers/CustomerDetailDialog.vue";
import CustomerFormDialog from "@/components/customers/CustomerFormDialog.vue";
import { contactLine, fullName, visitLabel } from "@/lib/customerFormat";
import { debounce } from "@/lib/debounce";
import { errorMessage } from "@/lib/errorMessage";
import { useCatalogAccess } from "@/composables/useCatalogAccess";
import { useCustomerStore, type Customer } from "@/stores/customer";
import { useSalonStore } from "@/stores/salon";
import { useSalonServiceStore } from "@/stores/salonService";

const route = useRoute();
const salonId = computed(() => String(route.params.salonId));

const salonStore = useSalonStore();
const serviceStore = useSalonServiceStore();
const customerStore = useCustomerStore();

const { canManage } = useCatalogAccess(() => salonId.value);

const ready = ref(false);
const loading = ref(false);
const error = ref<string | null>(null);
const query = ref("");
const pageIndex = ref(0);

const detailOpen = ref(false);
const selectedId = ref<string | null>(null);
const formOpen = ref(false);
const editing = ref<Customer | null>(null);

const timeZone = computed(() => salonStore.currentSalon?.timezone ?? "");

async function search() {
  loading.value = true;
  error.value = null;
  try {
    await customerStore.searchCustomers(salonId.value, {
      q: query.value,
      page: pageIndex.value,
    });
  } catch (e) {
    error.value = errorMessage(e, "Impossible de charger les clients.");
  } finally {
    loading.value = false;
  }
}

const debouncedSearch = debounce(() => {
  pageIndex.value = 0;
  search();
}, 300);

watch(query, () => debouncedSearch());
onBeforeUnmount(() => debouncedSearch.cancel());

function goToPage(delta: number) {
  pageIndex.value += delta;
  search();
}

watch(
  salonId,
  async (id) => {
    ready.value = false;
    error.value = null;
    query.value = "";
    pageIndex.value = 0;
    try {
      await Promise.all([
        salonStore.fetchSalon(id),
        serviceStore.fetchServices(id),
        salonStore.salons.length === 0
          ? salonStore.fetchSalons()
          : Promise.resolve(),
      ]);
      if (id !== salonId.value) {
        return;
      }
      ready.value = true;
      await search();
    } catch (e) {
      error.value = errorMessage(e, "Impossible de charger les clients.");
    }
  },
  { immediate: true },
);

function openDetail(customer: Customer) {
  selectedId.value = customer.customerProfileId;
  detailOpen.value = true;
}

function openCreate() {
  editing.value = null;
  formOpen.value = true;
}

/** Le formulaire remplace la fiche le temps de la modification. */
function onEdit(customer: Customer) {
  editing.value = customer;
  detailOpen.value = false;
  formOpen.value = true;
}

function onSaved(customer: Customer) {
  search();
  if (editing.value) {
    selectedId.value = customer.customerProfileId;
    detailOpen.value = true;
    editing.value = null;
  }
}
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div>
        <RouterLink
          :to="{ name: 'salon-detail', params: { salonId } }"
          class="text-sm text-wine hover:underline"
        >
          ← {{ salonStore.currentSalon?.name ?? "Salon" }}
        </RouterLink>
        <div class="mt-2 flex items-center justify-between gap-3">
          <h1 class="font-display text-2xl text-ink">
            Clients
          </h1>
          <RouterLink
            v-if="canManage"
            :to="{ name: 'customer-duplicates', params: { salonId } }"
            class="text-sm text-wine hover:underline"
          >
            Doublons
          </RouterLink>
        </div>
      </div>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>
      <p
        v-if="!ready && !error"
        class="text-sm text-ink/70"
      >
        Chargement…
      </p>

      <template v-else-if="ready">
        <div class="flex flex-wrap items-end gap-3">
          <div class="min-w-64 flex-1">
            <UiInput
              v-model="query"
              label="Rechercher un client"
              hint="Nom, email ou téléphone"
            />
          </div>
          <UiButton @click="openCreate">
            Nouveau client
          </UiButton>
        </div>

        <ul
          v-if="customerStore.results.length > 0"
          class="divide-y divide-border rounded-md border border-border bg-white"
          :class="{ 'opacity-60': loading }"
        >
          <li
            v-for="customer in customerStore.results"
            :key="customer.customerProfileId"
          >
            <button
              type="button"
              class="flex w-full flex-col gap-1 px-4 py-3 text-left hover:bg-paper sm:flex-row sm:items-center sm:justify-between"
              @click="openDetail(customer)"
            >
              <span>
                <span class="block font-medium text-ink">{{
                  fullName(customer)
                }}</span>
                <span class="block text-sm text-ink/60">{{
                  contactLine(customer) || "Aucun moyen de contact"
                }}</span>
              </span>
              <span class="text-xs text-ink/60 sm:text-right">
                <span class="block">Dernière visite :
                  {{ visitLabel(customer.lastVisitAt, timeZone) }}</span>
                <span class="block">Prochain rendez-vous :
                  {{ visitLabel(customer.nextVisitAt, timeZone) }}</span>
              </span>
            </button>
          </li>
        </ul>
        <p
          v-else-if="!loading"
          class="text-ink/60"
        >
          {{
            query.trim()
              ? "Aucun client ne correspond à cette recherche."
              : "Aucun client pour le moment."
          }}
        </p>

        <div
          v-if="customerStore.totalPages > 1"
          class="flex items-center justify-between"
        >
          <UiButton
            variant="secondary"
            size="sm"
            :disabled="pageIndex === 0 || loading"
            @click="goToPage(-1)"
          >
            ← Précédent
          </UiButton>
          <span class="text-sm text-ink/60">
            Page {{ pageIndex + 1 }} / {{ customerStore.totalPages }} ·
            {{ customerStore.totalElements }} clients
          </span>
          <UiButton
            variant="secondary"
            size="sm"
            :disabled="pageIndex + 1 >= customerStore.totalPages || loading"
            @click="goToPage(1)"
          >
            Suivant →
          </UiButton>
        </div>

        <CustomerDetailDialog
          v-model:open="detailOpen"
          :salon-id="salonId"
          :time-zone="timeZone"
          :customer-id="selectedId"
          @edit="onEdit"
          @changed="search"
        />
        <CustomerFormDialog
          v-model:open="formOpen"
          :salon-id="salonId"
          :customer="editing"
          @saved="onSaved"
        />
      </template>
    </div>
  </main>
</template>
