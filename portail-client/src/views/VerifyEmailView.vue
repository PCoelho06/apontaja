<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { useCooldown } from "@/composables/useCooldown";
import { ApiError } from "@apontaja/web-core/api-client";
import { TOO_MANY_ATTEMPTS } from "@apontaja/web-core/auth-errors";
import { useAuthStore } from "@/stores/auth";
import { useVerificationStore } from "@/stores/verification";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

const RESEND_DELAY_SECONDS = 60;

const auth = useAuthStore();
const verification = useVerificationStore();
const cooldown = useCooldown(RESEND_DELAY_SECONDS);

const emailInput = ref("");
const errorText = ref("");
const isSent = ref(false);
const isSubmitting = ref(false);

async function resend(email: string) {
  errorText.value = "";
  isSent.value = false;
  isSubmitting.value = true;
  try {
    await auth.resendVerificationEmail(email);
    isSent.value = true;
    cooldown.start();
  } catch (error) {
    if (error instanceof ApiError && error.status === 429) {
      errorText.value = TOO_MANY_ATTEMPTS;
    } else {
      // Réponse volontairement neutre : on ne révèle pas si l'adresse correspond à un compte.
      isSent.value = true;
      cooldown.start();
    }
  } finally {
    isSubmitting.value = false;
  }
}

async function handleResend() {
  if (verification.pendingEmail) {
    await resend(verification.pendingEmail);
  }
}

async function handleSubmitEmail() {
  const email = emailInput.value.trim();
  verification.remember(email);
  await resend(email);
}
</script>

<template>
  <AuthLayout title="Verifique o seu email">
    <div
      v-if="verification.pendingEmail"
      class="space-y-4"
    >
      <p class="text-sm text-ink">
        Vai receber um email em
        <strong>{{ verification.pendingEmail }}</strong>.
        Se acabou de criar a conta, clique na ligação de confirmação para a
        ativar e depois inicie sessão. Se já tinha uma conta, o email indica
        como iniciar sessão.
      </p>

      <UiAlert
        v-if="isSent"
        variant="success"
      >
        Se for possível o envio, acabámos de enviar um novo email.
      </UiAlert>
      <UiAlert
        v-if="errorText"
        variant="error"
      >
        {{ errorText }}
      </UiAlert>

      <UiButton
        variant="secondary"
        :disabled="cooldown.isActive.value"
        :loading="isSubmitting"
        @click="handleResend"
      >
        {{
          cooldown.isActive.value
            ? `Reenviar o email (${cooldown.remaining.value} s)`
            : "Reenviar o email"
        }}
      </UiButton>
    </div>

    <form
      v-else
      class="space-y-5"
      @submit.prevent="handleSubmitEmail"
    >
      <p class="text-sm text-ink">
        Indique o endereço usado na inscrição: voltaremos a enviar-lhe a ligação de
        confirmação.
      </p>
      <UiInput
        v-model="emailInput"
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
        Reenviar o email
      </UiButton>
    </form>

    <p class="mt-6 text-sm text-ink/70">
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Voltar ao início de sessão
      </RouterLink>
    </p>
  </AuthLayout>
</template>


