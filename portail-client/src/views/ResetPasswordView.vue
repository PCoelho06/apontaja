<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { ApiError } from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

const route = useRoute();
const auth = useAuthStore();

const token = typeof route.query.token === "string" ? route.query.token : "";
const newPassword = ref("");
const errorText = ref("");
const fieldErrors = ref<Record<string, string>>({});
const isSubmitting = ref(false);
const isDone = ref(false);

async function handleSubmit() {
  errorText.value = "";
  fieldErrors.value = {};
  isSubmitting.value = true;
  try {
    await auth.resetPassword(token, newPassword.value);
    isDone.value = true;
  } catch (error) {
    if (error instanceof ApiError) {
      errorText.value = error.message;
      fieldErrors.value = error.fieldErrors ?? {};
    } else {
      errorText.value = "Impossible de réinitialiser le mot de passe.";
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout title="Nouveau mot de passe">
    <UiAlert
      v-if="!token"
      variant="error"
    >
      Lien invalide — merci de repartir depuis l'email reçu.
    </UiAlert>

    <div
      v-else-if="isDone"
      class="space-y-4"
    >
      <UiAlert variant="success">
        Votre mot de passe a été mis à jour. Vous pouvez maintenant vous connecter.
      </UiAlert>
      <RouterLink
        :to="{ name: 'login' }"
        class="text-sm text-wine hover:underline"
      >
        Se connecter
      </RouterLink>
    </div>

    <form
      v-else
      class="space-y-5"
      @submit.prevent="handleSubmit"
    >
      <UiInput
        v-model="newPassword"
        type="password"
        label="Nouveau mot de passe"
        hint="12 caractères minimum"
        autocomplete="new-password"
        minlength="12"
        required
        :error="fieldErrors.newPassword"
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
        {{ isSubmitting ? "Enregistrement..." : "Enregistrer" }}
      </UiButton>
    </form>
  </AuthLayout>
</template>
