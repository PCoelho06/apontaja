<script setup lang="ts">
import { useRouter } from "vue-router";

import { useAuthStore } from "@/stores/auth";

import { UiButton } from "@apontaja/ui-kit";

const router = useRouter();
const auth = useAuthStore();

function goToAppointments() {
  void router.push({ name: "my-appointments" });
}
</script>

<template>
  <div class="min-h-screen bg-paper">
    <header class="border-b border-border">
      <div class="mx-auto flex max-w-2xl flex-wrap items-center justify-between gap-3 px-6 py-4">
        <p class="font-display text-xl text-wine">
          Apontaja
        </p>
        <div class="flex flex-wrap items-center gap-2">
          <UiButton
            v-if="auth.isAuthenticated"
            variant="secondary"
            size="sm"
            @click="goToAppointments"
          >
            As minhas marcações
          </UiButton>
          <slot name="actions" />
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-2xl px-6 py-10">
      <slot />
    </main>
  </div>
</template>
