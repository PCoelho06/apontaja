<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

import { ApiError } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";
import { useSalonStore } from "@/stores/salon";

const router = useRouter();
const auth = useAuthStore();
const salonStore = useSalonStore();

const isLoading = ref(true);
const errorMessage = ref("");

onMounted(async () => {
  try {
    await salonStore.fetchSalons();
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError
        ? error.message
        : "Impossible de charger vos salons.";
  } finally {
    isLoading.value = false;
  }
});

async function handleLogout() {
  try {
    await auth.logout();
  } finally {
    await router.push({ name: "login" });
  }
}
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-12">
    <div class="mx-auto max-w-2xl">
      <div class="flex items-center justify-between">
        <p class="font-display text-2xl text-ink">
          Bienvenue, {{ auth.account?.email }}
        </p>
        <button
          type="button"
          class="rounded-md border border-wine px-3 py-1.5 text-sm font-medium text-wine transition hover:bg-wine hover:text-paper"
          @click="handleLogout"
        >
          Se déconnecter
        </button>
      </div>

      <div class="mt-8 flex items-center justify-between">
        <h2 class="text-lg font-medium text-ink">
          Vos salons
        </h2>
        <RouterLink
          :to="{ name: 'create-salon' }"
          class="rounded-md bg-wine px-3 py-1.5 text-sm font-medium text-paper hover:bg-wine/90"
        >
          + Créer un salon
        </RouterLink>
      </div>

      <p
        v-if="isLoading"
        class="mt-4 text-sm text-ink/70"
      >
        Chargement...
      </p>
      <p
        v-else-if="errorMessage"
        class="mt-4 text-sm text-danger"
      >
        {{ errorMessage }}
      </p>
      <p
        v-else-if="salonStore.salons.length === 0"
        class="mt-4 text-sm text-ink/70"
      >
        Aucun salon pour l'instant.
      </p>

      <ul
        v-else
        class="mt-4 divide-y divide-border rounded-md border border-border bg-white"
      >
        <li
          v-for="salon in salonStore.salons"
          :key="salon.salonId"
        >
          <RouterLink
            :to="{ name: 'salon-detail', params: { salonId: salon.salonId } }"
            class="flex items-center justify-between px-4 py-3 hover:bg-paper"
          >
            <span>
              <span class="block text-sm font-medium text-ink">{{
                salon.name
              }}</span>
              <span class="block text-xs text-ink/60">{{ salon.city }}</span>
            </span>
            <span class="text-xs font-medium uppercase text-wine">{{
              salon.role
            }}</span>
          </RouterLink>
        </li>
      </ul>
    </div>
  </main>
</template>
