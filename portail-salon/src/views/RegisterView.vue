<script setup lang="ts">
import { ref } from "vue";
import { RouterLink } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { ApiError } from "@apontaja/web-core/api-client";
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
      "Vous devez accepter les CGU et la politique de confidentialité pour créer un compte.";
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
    if (error instanceof ApiError) {
      errorMessage.value = error.message;
      fieldErrors.value = error.fieldErrors ?? {};
    } else {
      errorMessage.value = "Impossible de créer le compte, réessayez.";
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Créer un compte"
    subtitle="Quelques informations pour démarrer."
  >
    <div
      v-if="isDone"
      class="space-y-4"
    >
      <p class="text-sm text-ink">
        Compte créé. Un email de confirmation vient de vous être envoyé —
        vérifiez votre boîte de réception, puis connectez-vous.
      </p>
      <RouterLink
        :to="{ name: 'login' }"
        class="inline-block rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper hover:bg-wine-hover"
      >
        Aller à la connexion
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
          label="Prénom"
          autocomplete="given-name"
          maxlength="100"
          required
          :error="fieldErrors.firstName"
        />
        <UiInput
          v-model="lastName"
          label="Nom"
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
        label="Mot de passe"
        hint="12 caractères minimum"
        autocomplete="new-password"
        minlength="12"
        required
        :error="fieldErrors.password"
      />

      <div>
        <UiCheckbox
          v-model="acceptTerms"
          label="J'accepte les CGU et la politique de confidentialité"
        />
        <div class="mt-1 flex gap-4 pl-6 text-xs">
          <RouterLink
            :to="{ name: 'terms' }"
            target="_blank"
            rel="noopener"
            class="text-wine hover:underline"
          >
            Lire les CGU
          </RouterLink>
          <RouterLink
            :to="{ name: 'privacy' }"
            target="_blank"
            rel="noopener"
            class="text-wine hover:underline"
          >
            Lire la politique de confidentialité
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
        {{ isSubmitting ? "Création..." : "Créer mon compte" }}
      </UiButton>
    </form>

    <p
      v-if="!isDone"
      class="mt-6 text-sm text-ink/70"
    >
      Déjà un compte ?
      <RouterLink
        :to="{ name: 'login' }"
        class="text-wine hover:underline"
      >
        Se connecter
      </RouterLink>
    </p>
  </AuthLayout>
</template>
