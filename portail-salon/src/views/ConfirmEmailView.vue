<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeConfirmError } from "@apontaja/web-core/auth-errors";
import { useAuthStore } from "@/stores/auth";

import { UiAlert } from "@apontaja/ui-kit";

const route = useRoute();
const auth = useAuthStore();

const status = ref<"pending" | "success" | "error">("pending");
const errorMessage = ref("");

onMounted(async () => {
  const token = typeof route.query.token === "string" ? route.query.token : "";
  if (!token) {
    status.value = "error";
    errorMessage.value =
      "Ligação inválida — volte a abrir a ligação que recebeu por email.";
    return;
  }

  try {
    await auth.confirmEmail(token);
    status.value = "success";
  } catch (error) {
    status.value = "error";
    errorMessage.value = describeConfirmError(error);
  }
});
</script>

<template>
  <AuthLayout title="Confirmação do email">
    <p
      v-if="status === 'pending'"
      class="text-sm text-ink"
    >
      A verificar...
    </p>
    <UiAlert
      v-else-if="status === 'success'"
      variant="success"
    >
      O seu email está confirmado.
    </UiAlert>
    <UiAlert
      v-else
      variant="error"
    >
      {{ errorMessage }}
    </UiAlert>

    <RouterLink
      :to="{ name: 'login' }"
      class="mt-6 inline-block text-sm text-wine hover:underline"
    >
      Voltar ao início de sessão
    </RouterLink>
  </AuthLayout>
</template>
