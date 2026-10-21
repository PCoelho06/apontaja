<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, useRoute } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";

import { UiAlert } from "@apontaja/ui-kit";

const route = useRoute();
const title = computed(() =>
  typeof route.meta.title === "string" ? route.meta.title : "Document",
);
</script>

<template>
  <AuthLayout :title="title">
    <UiAlert variant="info">
      Ce document est en cours de rédaction et sera publié prochainement.
    </UiAlert>
    <RouterLink
      :to="{ name: 'home' }"
      class="mt-6 inline-block text-sm text-wine hover:underline"
    >
      Retour à l'accueil
    </RouterLink>
  </AuthLayout>
</template>
