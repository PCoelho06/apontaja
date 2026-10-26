<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import { apiGet, apiPost } from "@apontaja/web-core/api-client";
import {
  describeLoginError,
  describeRegisterError,
} from "@apontaja/web-core/auth-errors";
import { describeAcceptInvitationError } from "@/lib/salonErrors";
import { roleLabel } from "@/lib/staffRoles";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiButton, UiCheckbox, UiInput } from "@apontaja/ui-kit";

interface LookupResponse {
  invitationId: string;
  email: string;
  role: string;
  salonName: string;
  accountExists: boolean;
  expiresAt: string;
}

const route = useRoute();
const auth = useAuthStore();
const token = computed(() =>
  typeof route.query.token === "string" ? route.query.token : "",
);

type Step =
  | "loading"
  | "invalid"
  | "mismatch"
  | "need-login"
  | "need-register"
  | "ready"
  | "success"
  | "error";

const step = ref<Step>("loading");
const lookup = ref<LookupResponse | null>(null);
const errorMessage = ref("");

const password = ref("");
const firstName = ref("");
const lastName = ref("");
const acceptTerms = ref(false);
const isSubmitting = ref(false);

async function load() {
  if (!token.value) {
    step.value = "invalid";
    return;
  }
  try {
    lookup.value = await apiGet<LookupResponse>(
      `/api/staff-invitations/${token.value}`,
    );
  } catch {
    step.value = "invalid";
    return;
  }

  if (auth.isAuthenticated) {
    step.value =
      auth.account?.email === lookup.value.email ? "ready" : "mismatch";
  } else {
    step.value = lookup.value.accountExists ? "need-login" : "need-register";
  }
}

onMounted(load);

async function acceptInvitation() {
  isSubmitting.value = true;
  errorMessage.value = "";
  try {
    await apiPost<void>(
      "/api/staff-invitations/accept",
      {
        token: token.value,
      },
      auth.accessToken ?? undefined,
    );
    step.value = "success";
  } catch (error) {
    errorMessage.value = describeAcceptInvitationError(error);
    step.value = "error";
  } finally {
    isSubmitting.value = false;
  }
}

async function handleLogin() {
  if (!lookup.value) return;
  isSubmitting.value = true;
  errorMessage.value = "";
  try {
    await auth.login(lookup.value.email, password.value);
  } catch (error) {
    errorMessage.value = describeLoginError(error);
    isSubmitting.value = false;
    return;
  }
  await acceptInvitation();
}

async function handleRegisterThenLogin() {
  if (!lookup.value) return;
  if (!acceptTerms.value) {
    errorMessage.value =
      "Tem de aceitar os Termos e Condições e a Política de Privacidade.";
    return;
  }
  isSubmitting.value = true;
  errorMessage.value = "";
  try {
    await auth.register({
      email: lookup.value.email,
      password: password.value,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      acceptTerms: acceptTerms.value,
    });
    // Pas de connexion automatique après /register (voir stores/auth.ts) — on enchaîne nous-mêmes.
    await auth.login(lookup.value.email, password.value);
  } catch (error) {
    const failure = describeRegisterError(error);
    errorMessage.value =
      Object.values(failure.fieldErrors)[0] ?? failure.message;
    isSubmitting.value = false;
    return;
  }
  await acceptInvitation();
}
</script>

<template>
  <main
    class="flex min-h-screen items-center justify-center bg-paper px-6 py-12"
  >
    <div class="w-full max-w-sm">
      <p
        v-if="step === 'loading'"
        class="text-sm text-ink/70"
      >
        A carregar...
      </p>

      <UiAlert
        v-else-if="step === 'invalid'"
        variant="error"
      >
        Esta ligação de convite é inválida ou expirou.
      </UiAlert>

      <UiAlert
        v-else-if="step === 'mismatch'"
        variant="error"
      >
        Este convite foi enviado para {{ lookup?.email }}, um endereço
        diferente do da conta com sessão iniciada ({{ auth.account?.email }}).
        Termine a sessão e volte a tentar com a conta correta.
      </UiAlert>

      <template v-else-if="step === 'ready'">
        <p class="font-display text-xl text-ink">
          Juntar-se a {{ lookup?.salonName }}
        </p>
        <p class="mt-2 text-sm text-ink/70">
          Função proposta: {{ roleLabel(lookup?.role ?? "") }}
        </p>
        <UiAlert
          v-if="errorMessage"
          variant="error"
          class="mt-4"
        >
          {{ errorMessage }}
        </UiAlert>
        <UiButton
          class="mt-6 w-full"
          :loading="isSubmitting"
          @click="acceptInvitation"
        >
          {{ isSubmitting ? "A aceitar..." : "Aceitar o convite" }}
        </UiButton>
      </template>

      <template v-else-if="step === 'need-login'">
        <p class="font-display text-xl text-ink">
          Juntar-se a {{ lookup?.salonName }}
        </p>
        <p class="mt-2 text-sm text-ink/70">
          Inicie sessão com {{ lookup?.email }} para aceitar este convite.
        </p>
        <form
          class="mt-4 space-y-4"
          @submit.prevent="handleLogin"
        >
          <UiInput
            v-model="password"
            type="password"
            label="Palavra-passe"
            autocomplete="current-password"
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
            {{ isSubmitting ? "A iniciar sessão..." : "Iniciar sessão e aceitar" }}
          </UiButton>
        </form>
      </template>

      <template v-else-if="step === 'need-register'">
        <p class="font-display text-xl text-ink">
          Juntar-se a {{ lookup?.salonName }}
        </p>
        <p class="mt-2 text-sm text-ink/70">
          Crie uma conta com {{ lookup?.email }} para aceitar este convite.
        </p>
        <form
          class="mt-4 space-y-4"
          @submit.prevent="handleRegisterThenLogin"
        >
          <div class="grid grid-cols-2 gap-4">
            <UiInput
              v-model="firstName"
              label="Nome próprio"
              autocomplete="given-name"
              maxlength="100"
              required
            />
            <UiInput
              v-model="lastName"
              label="Apelido"
              autocomplete="family-name"
              maxlength="100"
              required
            />
          </div>
          <UiInput
            v-model="password"
            type="password"
            label="Palavra-passe"
            hint="Mínimo de 12 caracteres"
            autocomplete="new-password"
            minlength="12"
            required
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
            {{ isSubmitting ? "A criar..." : "Criar a conta e aceitar" }}
          </UiButton>
        </form>
      </template>

      <p
        v-else-if="step === 'success'"
        class="text-sm text-ink"
      >
        Convite aceite! Já pode aceder a este salão a partir do seu espaço.
        <RouterLink
          :to="{ name: 'home' }"
          class="text-wine hover:underline"
        >
          Voltar ao início
        </RouterLink>
      </p>

      <UiAlert
        v-else-if="step === 'error'"
        variant="error"
      >
        {{ errorMessage }}
      </UiAlert>
    </div>
  </main>
</template>
