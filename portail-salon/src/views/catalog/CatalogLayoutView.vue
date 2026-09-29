<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

import { errorMessage } from "@/lib/errorMessage";
import { useSalonStore } from "@/stores/salon";

const route = useRoute();
const salonStore = useSalonStore();

const salonId = computed(() => String(route.params.salonId));
const ready = ref(false);
const error = ref<string | null>(null);

watch(
  salonId,
  async (id) => {
    ready.value = false;
    error.value = null;
    try {
      await Promise.all([
        salonStore.fetchSalon(id),
        salonStore.salons.length === 0
          ? salonStore.fetchSalons()
          : Promise.resolve(),
      ]);
      ready.value = true;
    } catch (e) {
      error.value = errorMessage(e, "Impossible de charger le salon.");
    }
  },
  { immediate: true },
);

const tabClass =
  "rounded-md px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-100";
const activeTabClass = "!bg-gray-900 !text-white";
</script>

<template>
  <main class="mx-auto max-w-4xl space-y-6 p-6">
    <header class="space-y-3">
      <RouterLink
        :to="{ name: 'salon-detail', params: { salonId } }"
        class="text-sm text-gray-500 hover:underline"
      >
        ← {{ salonStore.currentSalon?.name ?? "Salon" }}
      </RouterLink>
      <h1 class="text-2xl font-semibold">
        Catalogue
      </h1>
      <nav class="flex gap-2">
        <RouterLink
          :to="{ name: 'catalog-resources', params: { salonId } }"
          :class="tabClass"
          :active-class="activeTabClass"
        >
          Ressources
        </RouterLink>
        <RouterLink
          :to="{ name: 'catalog-services', params: { salonId } }"
          :class="tabClass"
          :active-class="activeTabClass"
        >
          Prestations
        </RouterLink>
      </nav>
    </header>

    <p
      v-if="error"
      role="alert"
      class="text-red-600"
    >
      {{ error }}
    </p>
    <RouterView v-else-if="ready" />
    <p
      v-else
      class="text-gray-500"
    >
      Chargement…
    </p>
  </main>
</template>
