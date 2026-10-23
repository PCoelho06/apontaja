<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";

import { useAuthStore } from "@/stores/auth";

import { UiButton } from "@apontaja/ui-kit";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

function goToLogin() {
  void router.push({ name: "login", query: { redirect: route.fullPath } });
}

function goToAccount() {
  void router.push({ name: "home" });
}
</script>

<template>
  <UiButton
    v-if="auth.isAuthenticated"
    variant="secondary"
    size="sm"
    @click="goToAccount"
  >
    A minha conta
  </UiButton>
  <UiButton
    v-else
    variant="secondary"
    size="sm"
    @click="goToLogin"
  >
    Iniciar sessão
  </UiButton>
</template>
