<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

import { UiAlert } from "@apontaja/ui-kit";

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
  "rounded-md px-3 py-1.5 text-sm font-medium text-ink/70 hover:bg-ink/5";
const activeTabClass = "bg-wine! text-paper! hover:bg-wine!";
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-8">
    <div class="mx-auto max-w-4xl space-y-6">
      <header class="space-y-3">
        <RouterLink
          :to="{ name: 'salon-detail', params: { salonId } }"
          class="text-sm text-wine hover:underline"
        >
          ← {{ salonStore.currentSalon?.name ?? "Salon" }}
        </RouterLink>
        <h1 class="font-display text-2xl text-ink">
          Catalogue
        </h1>
        <nav class="flex flex-wrap gap-2">
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
          <RouterLink
            :to="{ name: 'catalog-schedule', params: { salonId } }"
            :class="tabClass"
            :active-class="activeTabClass"
          >
            Horaires
          </RouterLink>
          <RouterLink
            :to="{ name: 'catalog-closures', params: { salonId } }"
            :class="tabClass"
            :active-class="activeTabClass"
          >
            Fermetures
          </RouterLink>
        </nav>
      </header>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>
      <RouterView v-else-if="ready" />
      <p
        v-else
        class="text-sm text-ink/70"
      >
        Chargement…
      </p>
    </div>
  </main>
</template>
