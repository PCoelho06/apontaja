<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";

import AccountLayout from "@/layouts/AccountLayout.vue";
import { useCooldown } from "@/composables/useCooldown";
import { ApiError } from "@apontaja/web-core/api-client";
import { useAuthStore } from "@/stores/auth";

import { UiAlert, UiBadge, UiButton } from "@apontaja/ui-kit";

const RESEND_DELAY_SECONDS = 60;

const router = useRouter();
const auth = useAuthStore();
const cooldown = useCooldown(RESEND_DELAY_SECONDS);

const errorText = ref("");
const isSent = ref(false);
const isSending = ref(false);
const isRefreshing = ref(false);

async function handleLogout() {
  try {
    await auth.logout();
  } finally {
    await router.push({ name: "login" });
  }
}

function goToAppointments() {
  void router.push({ name: "my-appointments" });
}

async function handleResend() {
  const email = auth.account?.email;
  if (!email) return;

  errorText.value = "";
  isSent.value = false;
  isSending.value = true;
  try {
    await auth.resendVerificationEmail(email);
    isSent.value = true;
    cooldown.start();
  } catch (error) {
    if (error instanceof ApiError && error.status === 429) {
      errorText.value = "Demasiadas tentativas. Tente novamente mais tarde.";
    } else {
      isSent.value = true;
      cooldown.start();
    }
  } finally {
    isSending.value = false;
  }
}

async function handleRefresh() {
  errorText.value = "";
  isRefreshing.value = true;
  try {
    await auth.loadAccount();
  } catch {
    errorText.value = "Não foi possível atualizar a sua conta.";
  } finally {
    isRefreshing.value = false;
  }
}
</script>

<template>
  <AccountLayout>
    <template #actions>
      <UiButton
        variant="secondary"
        size="sm"
        @click="handleLogout"
      >
        Terminar sessão
      </UiButton>
    </template>

    <h1 class="font-display text-2xl text-ink">
      Olá, {{ auth.account?.firstName }}
    </h1>

    <section class="mt-8 rounded-md border border-border bg-white p-5">
      <h2 class="text-sm font-medium text-ink/70">
        A minha conta
      </h2>
      <dl class="mt-4 space-y-3 text-sm">
        <div class="flex justify-between gap-4">
          <dt class="text-ink/70">
            Nome
          </dt>
          <dd class="text-ink">
            {{ auth.account?.firstName }} {{ auth.account?.lastName }}
          </dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-ink/70">
            Email
          </dt>
          <dd class="text-ink">
            {{ auth.account?.email }}
          </dd>
        </div>
        <div class="flex items-center justify-between gap-4">
          <dt class="text-ink/70">
            Estado do email
          </dt>
          <dd>
            <UiBadge :tone="auth.account?.emailVerified ? 'brass' : 'wine'">
              {{ auth.account?.emailVerified ? "Email verificado" : "Por verificar" }}
            </UiBadge>
          </dd>
        </div>
      </dl>
    </section>

    <section class="mt-6 rounded-md border border-border bg-white p-5">
      <h2 class="text-sm font-medium text-ink/70">
        As minhas marcações
      </h2>
      <p class="mt-3 text-sm text-ink">
        Consulte as suas marcações e cancele-as, se necessário.
      </p>
      <UiButton
        class="mt-4"
        variant="secondary"
        @click="goToAppointments"
      >
        Ver as minhas marcações
      </UiButton>
    </section>

    <section
      v-if="auth.account && !auth.account.emailVerified"
      class="mt-6 space-y-4"
    >
      <UiAlert variant="info">
        O seu endereço de email ainda não está confirmado. Precisa dele para marcar:
        clique na ligação que recebeu por email.
      </UiAlert>
      <UiAlert
        v-if="isSent"
        variant="success"
      >
        Se for possível, enviámos-lhe um novo email.
      </UiAlert>
      <UiAlert
        v-if="errorText"
        variant="error"
      >
        {{ errorText }}
      </UiAlert>

      <div class="flex flex-wrap gap-3">
        <UiButton
          variant="secondary"
          :disabled="cooldown.isActive.value"
          :loading="isSending"
          @click="handleResend"
        >
          {{
            cooldown.isActive.value
              ? `Reenviar email (${cooldown.remaining.value} s)`
              : "Reenviar email"
          }}
        </UiButton>
        <UiButton
          variant="secondary"
          :loading="isRefreshing"
          @click="handleRefresh"
        >
          Já confirmei, atualizar
        </UiButton>
      </div>
    </section>
  </AccountLayout>
</template>
