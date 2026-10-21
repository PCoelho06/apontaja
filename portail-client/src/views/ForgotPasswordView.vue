<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { ApiError } from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

const auth = useAuthStore();

const email = ref("");
const errorText = ref("");
const isSubmitting = ref(false);
const isDone = ref(false);

async function handleSubmit() {
  errorText.value = "";
  isSubmitting.value = true;
  try {
    await auth.requestPasswordReset(email.value);
    isDone.value = true;
  } catch (error) {
    if (error instanceof ApiError && error.status === 429) {
      errorText.value = "Trop de tentatives, réessayez plus tard.";
    } else {
      // Réponse volontairement neutre (anti-énumération de comptes), comme côté back.
      isDone.value = true;
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Mot de passe oublié"
    subtitle="Recevez un lien pour en choisir un nouveau."
  >
    <div
      v-if="isDone"
      class="space-y-4"
    >
      <UiAlert variant="success">
        Si un compte existe pour cette adresse, un email contenant un lien de
        réinitialisation vient d'être envoyé.
      </UiAlert>
      <RouterLink
        :to="{ name: 'login' }"
        class="text-sm text-wine hover:underline"
      >
        Retour à la connexion
      </RouterLink>
    </div>

    <form
      v-else
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
        {{ isSubmitting ? "Envoi..." : "Envoyer le lien" }}
      </UiButton>
    </form>

    <p
      v-if="!isDone"
      class="mt-6 text-sm text-ink/70"
    >
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Retour à la connexion
      </RouterLink>
    </p>
  </AuthLayout>
</template>
