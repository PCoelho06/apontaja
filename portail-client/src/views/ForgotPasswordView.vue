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
      errorText.value = TOO_MANY_ATTEMPTS;
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
    title="Palavra-passe esquecida"
    subtitle="Receba uma ligação para escolher uma nova."
  >
    <div
      v-if="isDone"
      class="space-y-4"
    >
      <UiAlert variant="success">
        Se existir uma conta para este endereço, acabámos de enviar um email com uma
        ligação para repor a palavra-passe.
      </UiAlert>
      <RouterLink
        :to="{ name: 'login' }"
        class="text-sm text-wine hover:underline"
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
        {{ isSubmitting ? "A enviar..." : "Enviar a ligação" }}
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


