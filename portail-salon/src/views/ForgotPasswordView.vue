<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

const auth = useAuthStore();
const email = ref("");
const errorMessage = ref("");
const isSubmitting = ref(false);
const isDone = ref(false);

async function handleSubmit() {
  errorMessage.value = "";
  isSubmitting.value = true;
  try {
    await auth.requestPasswordReset(email.value);
    isDone.value = true;
  } catch (error) {
    if (error instanceof ApiError && error.status === 429) {
      errorMessage.value = TOO_MANY_ATTEMPTS;
    } else {
      // Le backend renvoie normalement toujours 204 ici (anti-énumération).
      // Si une erreur arrive quand même, on ne distingue pas la cause pour
      // ne pas recréer une fuite d'information par un autre chemin.
      isDone.value = true;
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Recuperar palavra-passe"
    subtitle="Receba uma ligação para escolher uma nova."
  >
    <div
      v-if="isDone"
      class="space-y-4 text-sm text-ink"
    >
      <p>
        Se existir uma conta com este endereço, enviámos um email com uma
        ligação para repor a palavra-passe.
      </p>
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Voltar ao início de sessão
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
        {{ isSubmitting ? "A enviar..." : "Enviar ligação" }}
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
        Voltar ao início de sessão
      </RouterLink>
    </p>
  </AuthLayout>
</template>
