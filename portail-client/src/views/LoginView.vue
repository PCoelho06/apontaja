<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { safeRedirect } from "@/lib/safeRedirect";
import { errorMessage } from "@apontaja/web-core/error-message";
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
    errorText.value = errorMessage(error, "Connexion impossible, réessayez.");
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Se connecter"
    subtitle="Accédez à votre compte pour réserver."
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
        label="Mot de passe"
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
        {{ isSubmitting ? "Connexion..." : "Se connecter" }}
      </UiButton>
    </form>

    <p class="mt-6 text-sm text-ink/70">
      <RouterLink
        :to="{ name: 'forgot-password' }"
        class="text-wine hover:underline"
      >
        Mot de passe oublié ?
      </RouterLink>
    </p>
    <p class="mt-2 text-sm text-ink/70">
      Pas encore de compte ?
      <RouterLink
        :to="{ name: 'register' }"
        class="text-wine hover:underline"
      >
        Créer un compte
      </RouterLink>
    </p>
  </AuthLayout>
</template>



