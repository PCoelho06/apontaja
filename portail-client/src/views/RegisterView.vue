<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeRegisterError } from "@apontaja/web-core/auth-errors";
import { useAuthStore } from "@/stores/auth";
import { useVerificationStore } from "@/stores/verification";

import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiInput,
} from "@apontaja/ui-kit";

const router = useRouter();
const auth = useAuthStore();
const verification = useVerificationStore();

const firstName = ref("");
const lastName = ref("");
const email = ref("");
const password = ref("");
const acceptTerms = ref(false);

const errorText = ref("");
const fieldErrors = ref<Record<string, string>>({});
const isSubmitting = ref(false);

async function handleSubmit() {
  errorText.value = "";
  fieldErrors.value = {};

  if (!acceptTerms.value) {
    errorText.value =
      "Tem de aceitar os Termos e Condições e a Política de Privacidade para criar uma conta.";
    return;
  }

  isSubmitting.value = true;
  try {
    await auth.register({
      email: email.value,
      password: password.value,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      acceptTerms: acceptTerms.value,
    });
    verification.remember(email.value);
    await router.push({ name: "verify-email" });
  } catch (error) {
    const failure = describeRegisterError(error);
    errorText.value = failure.message;
    fieldErrors.value = failure.fieldErrors;
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Criar conta"
    subtitle="Uma conta para fazer marcações em todos os seus salões."
  >
    <form
      class="space-y-5"
      @submit.prevent="handleSubmit"
    >
      <div class="grid grid-cols-2 gap-4">
        <UiInput
          v-model="firstName"
          label="Nome próprio"
          autocomplete="given-name"
          maxlength="100"
          required
          :error="fieldErrors.firstName"
        />
        <UiInput
          v-model="lastName"
          label="Apelido"
          autocomplete="family-name"
          maxlength="100"
          required
          :error="fieldErrors.lastName"
        />
      </div>
      <UiInput
        v-model="email"
        type="email"
        label="Email"
        autocomplete="email"
        required
        :error="fieldErrors.email"
      />
      <UiInput
        v-model="password"
        type="password"
        label="Palavra-passe"
        hint="Mínimo de 12 caracteres"
        autocomplete="new-password"
        minlength="12"
        required
        :error="fieldErrors.password"
      />

      <div>
        <UiCheckbox
          v-model="acceptTerms"
          label="Aceito os Termos e Condições e a Política de Privacidade"
        />
        <div class="mt-1 flex gap-4 pl-6 text-xs">
          <RouterLink
            :to="{ name: 'terms' }"
            target="_blank"
            rel="noopener"
            class="text-wine hover:underline"
          >
            Ler os Termos e Condições
          </RouterLink>
          <RouterLink
            :to="{ name: 'privacy' }"
            target="_blank"
            rel="noopener"
            class="text-wine hover:underline"
          >
            Ler a Política de Privacidade
          </RouterLink>
        </div>
      </div>

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
        {{ isSubmitting ? "A criar..." : "Criar a minha conta" }}
      </UiButton>
    </form>

    <p class="mt-6 text-sm text-ink/70">
      Já tem conta?
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Entrar
      </RouterLink>
    </p>
  </AuthLayout>
</template>


