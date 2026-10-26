<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeResetError } from "@apontaja/web-core/auth-errors";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

const route = useRoute();
const auth = useAuthStore();

const token = typeof route.query.token === "string" ? route.query.token : "";
const newPassword = ref("");
const errorMessage = ref("");
const fieldErrors = ref<Record<string, string>>({});
const isSubmitting = ref(false);
const isDone = ref(false);

async function handleSubmit() {
  errorMessage.value = "";
  fieldErrors.value = {};
  isSubmitting.value = true;
  try {
    await auth.resetPassword(token, newPassword.value);
    isDone.value = true;
  } catch (error) {
    const failure = describeResetError(error);
    errorMessage.value = failure.message;
    fieldErrors.value = failure.fieldErrors;
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout title="Nova palavra-passe">
    <UiAlert
      v-if="!token"
      variant="error"
    >
      Ligação inválida — volte a abrir a ligação que recebeu por email.
    </UiAlert>

    <div
      v-else-if="isDone"
      class="space-y-4 text-sm text-ink"
    >
      <p>A sua palavra-passe foi atualizada. Já pode iniciar sessão.</p>
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Iniciar sessão
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
        label="Nova palavra-passe"
        hint="Mínimo de 12 caracteres"
        autocomplete="new-password"
        minlength="12"
        required
        :error="fieldErrors.newPassword"
      />

      <UiAlert
        v-if="errorMessage"
        variant="error"
      >
        {{ errorMessage }}
      </UiAlert>

      <UiButton
        type="submit"
        class="w-full"
        :loading="isSubmitting"
      >
        {{ isSubmitting ? "A guardar..." : "Guardar" }}
      </UiButton>
    </form>
  </AuthLayout>
</template>
