<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";

import { ApiError, apiGet, apiPost } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";

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
    errorMessage.value =
      error instanceof ApiError
        ? error.message
        : "Impossible d'accepter l'invitation.";
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
    await acceptInvitation();
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : "Connexion impossible.";
    isSubmitting.value = false;
  }
}

async function handleRegisterThenLogin() {
  if (!lookup.value) return;
  isSubmitting.value = true;
  errorMessage.value = "";
  try {
    await auth.register(lookup.value.email, password.value);
    // Pas de connexion automatique après /register (voir stores/auth.ts) — on enchaîne nous-mêmes.
    await auth.login(lookup.value.email, password.value);
    await acceptInvitation();
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError
        ? error.message
        : "Impossible de créer le compte.";
    isSubmitting.value = false;
  }
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
        Chargement...
      </p>

      <p
        v-else-if="step === 'invalid'"
        class="text-sm text-danger"
      >
        Ce lien d'invitation est invalide ou a expiré.
      </p>

      <p
        v-else-if="step === 'mismatch'"
        class="text-sm text-danger"
      >
        Cette invitation a été envoyée à {{ lookup?.email }}, une adresse
        différente de votre compte connecté ({{ auth.account?.email }}).
        Déconnectez-vous puis réessayez avec le bon compte.
      </p>

      <template v-else-if="step === 'ready'">
        <p class="font-display text-xl text-ink">
          Rejoindre {{ lookup?.salonName }}
        </p>
        <p class="mt-2 text-sm text-ink/70">
          Rôle proposé : {{ lookup?.role }}
        </p>
        <button
          type="button"
          :disabled="isSubmitting"
          class="mt-6 w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper hover:bg-wine/90 disabled:opacity-60"
          @click="acceptInvitation"
        >
          {{ isSubmitting ? "Acceptation..." : "Accepter l'invitation" }}
        </button>
      </template>

      <template v-else-if="step === 'need-login'">
        <p class="font-display text-xl text-ink">
          Rejoindre {{ lookup?.salonName }}
        </p>
        <p class="mt-2 text-sm text-ink/70">
          Connectez-vous avec {{ lookup?.email }} pour accepter cette
          invitation.
        </p>
        <form
          class="mt-4 space-y-4"
          @submit.prevent="handleLogin"
        >
          <div>
            <label class="block text-sm font-medium text-ink">Mot de passe</label>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              class="mt-1.5 block w-full rounded-md border border-border bg-white px-3 py-2 text-sm text-ink focus:border-wine focus:outline-none focus:ring-1 focus:ring-wine"
            >
          </div>
          <button
            type="submit"
            :disabled="isSubmitting"
            class="w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper hover:bg-wine/90 disabled:opacity-60"
          >
            {{ isSubmitting ? "Connexion..." : "Se connecter et accepter" }}
          </button>
        </form>
      </template>

      <template v-else-if="step === 'need-register'">
        <p class="font-display text-xl text-ink">
          Rejoindre {{ lookup?.salonName }}
        </p>
        <p class="mt-2 text-sm text-ink/70">
          Créez un compte avec {{ lookup?.email }} pour accepter cette
          invitation.
        </p>
        <form
          class="mt-4 space-y-4"
          @submit.prevent="handleRegisterThenLogin"
        >
          <div>
            <label class="block text-sm font-medium text-ink">Mot de passe (12 caractères minimum)</label>
            <input
              v-model="password"
              type="password"
              autocomplete="new-password"
              class="mt-1.5 block w-full rounded-md border border-border bg-white px-3 py-2 text-sm text-ink focus:border-wine focus:outline-none focus:ring-1 focus:ring-wine"
            >
          </div>
          <button
            type="submit"
            :disabled="isSubmitting"
            class="w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper hover:bg-wine/90 disabled:opacity-60"
          >
            {{ isSubmitting ? "Création..." : "Créer le compte et accepter" }}
          </button>
        </form>
      </template>

      <p
        v-else-if="step === 'success'"
        class="text-sm text-ink"
      >
        Invitation acceptée ! Vous pouvez maintenant accéder à ce salon depuis
        votre espace.
        <RouterLink
          :to="{ name: 'home' }"
          class="text-wine hover:underline"
        >
          Retour à l'accueil
        </RouterLink>
      </p>

      <p
        v-else-if="step === 'error'"
        class="text-sm text-danger"
      >
        {{ errorMessage }}
      </p>
    </div>
  </main>
</template>
