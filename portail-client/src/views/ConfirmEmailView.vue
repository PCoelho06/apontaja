<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeConfirmError } from "@/lib/authErrors";
import { useAuthStore } from "@/stores/auth";
import { useVerificationStore } from "@/stores/verification";

import { UiAlert } from "@apontaja/ui-kit";

type Status = "loading" | "success" | "error" | "missing";

const route = useRoute();
const auth = useAuthStore();
const verification = useVerificationStore();

const token = typeof route.query.token === "string" ? route.query.token : "";
const status = ref<Status>(token ? "loading" : "missing");
const errorText = ref("");

onMounted(async () => {
  if (!token) return;

  try {
    await auth.confirmEmail(token);
    verification.clear();
    status.value = "success";
  } catch (error) {
    errorText.value = describeConfirmError(error);
    status.value = "error";
    return;
  }

  if (auth.isAuthenticated) {
    // L'email est confirmé ; si ce rafraîchissement échoue, l'état se mettra à jour au prochain chargement.
    await auth.loadAccount().catch(() => undefined);
  }
});
</script>

<template>
  <AuthLayout title="Confirmação do seu email">
    <p
      v-if="status === 'loading'"
      class="text-sm text-ink/70"
    >
      A confirmar...
    </p>

    <UiAlert
      v-else-if="status === 'missing'"
      variant="error"
    >
      Ligação inválida. Utilize a ligação do email que recebeu.
    </UiAlert>

    <div
      v-else-if="status === 'error'"
      class="space-y-4"
    >
      <UiAlert variant="error">
        {{ errorText }}
      </UiAlert>
      <RouterLink
        :to="{ name: 'verify-email' }"
        class="text-sm text-wine hover:underline"
      >
        Reenviar o email de confirmação
      </RouterLink>
    </div>

    <div
      v-else
      class="space-y-4"
    >
      <UiAlert variant="success">
        O seu endereço de email foi confirmado.
      </UiAlert>
      <RouterLink
        :to="{ name: auth.isAuthenticated ? 'home' : 'login' }"
        class="inline-block text-sm text-wine hover:underline"
      >
        {{ auth.isAuthenticated ? "Ir para a minha conta" : "Entrar" }}
      </RouterLink>
    </div>
  </AuthLayout>
</template>


