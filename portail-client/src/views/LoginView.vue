<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeLoginError } from "@apontaja/web-core/auth-errors";
import { safeRedirect } from "@/lib/safeRedirect";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const email = ref("");
const password = ref("");
const errorText = ref("");
const isSubmitting = ref(false);

async function handleSubmit() {
  errorText.value = "";
  isSubmitting.value = true;
  try {
    await auth.login(email.value, password.value);
    // Retour à la page demandée avant la connexion (ex. une réservation), sinon l'accueil.
    await router.push(safeRedirect(route.query.redirect) ?? { name: "home" });
  } catch (error) {
    errorText.value = describeLoginError(error);
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Entrar"
    subtitle="Aceda à sua conta para fazer marcações."
  >
    <form
      class="space-y-5"
      @submit.prevent="handleSubmit"
    >
      <UiInput
        v-model="email"
        type="email"
        label="Email"
        autocomplete="email"
        required
      />
      <UiInput
        v-model="password"
        type="password"
        label="Palavra-passe"
        autocomplete="current-password"
        required
      />

      <UiAlert
        v-if="errorText"
        variant="error"
      >
        {{ errorText }}
      </UiAlert>

      <UiButton
        type="submit"
        class="w-full"
        :loading="isSubmitting"
      >
        {{ isSubmitting ? "A entrar..." : "Entrar" }}
      </UiButton>
    </form>

    <p class="mt-6 text-sm text-ink/70">
      <RouterLink
        :to="{ name: 'forgot-password' }"
        class="text-wine hover:underline"
      >
        Esqueceu-se da palavra-passe?
      </RouterLink>
    </p>
    <p class="mt-2 text-sm text-ink/70">
      Ainda não tem conta?
      <RouterLink
        :to="{ name: 'register' }"
        class="text-wine hover:underline"
      >
        Criar conta
      </RouterLink>
    </p>
  </AuthLayout>
</template>





