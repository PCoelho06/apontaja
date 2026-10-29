<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeRegisterError } from "@apontaja/web-core/auth-errors";
import { useAuthStore } from "@/stores/auth";

import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiInput,
} from "@apontaja/ui-kit";

const auth = useAuthStore();

const firstName = ref("");
const lastName = ref("");
const email = ref("");
const password = ref("");
const acceptTerms = ref(false);

const errorMessage = ref("");
const fieldErrors = ref<Record<string, string>>({});
const isSubmitting = ref(false);
const isDone = ref(false);

async function handleSubmit() {
  errorMessage.value = "";
  fieldErrors.value = {};

  if (!acceptTerms.value) {
    errorMessage.value =
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
    isDone.value = true;
  } catch (error) {
    const failure = describeRegisterError(error);
    errorMessage.value = failure.message;
    fieldErrors.value = failure.fieldErrors;
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Criar conta"
    subtitle="Algumas informações para começar."
  >
    <div
      v-if="isDone"
      class="space-y-4"
    >
      <p class="text-sm text-ink">
        Pedido recebido. Vai receber um email em
        <strong>{{ email }}</strong>.
        Se acabou de criar a conta, clique na ligação de confirmação para a
        ativar e depois inicie sessão. Se já tinha uma conta, o email indica
        como iniciar sessão.
      </p>
      <RouterLink
        :to="{ name: 'login' }"
        class="inline-block rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper hover:bg-wine-hover"
      >
        Ir para o início de sessão
      </RouterLink>
    </div>

    <form
      v-else
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
        {{ isSubmitting ? "A criar..." : "Criar a minha conta" }}
      </UiButton>
    </form>

    <p
      v-if="!isDone"
      class="mt-6 text-sm text-ink/70"
    >
      Já tem conta?
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Iniciar sessão
      </RouterLink>
    </p>
  </AuthLayout>
</template>
