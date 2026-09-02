<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'

import AuthTextField from '@/components/AuthTextField.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const email = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)
const isDone = ref(false)

async function handleSubmit() {
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await auth.requestPasswordReset(email.value)
    isDone.value = true
  } catch (error) {
    if (error instanceof ApiError && error.status === 429) {
      errorMessage.value = 'Trop de tentatives, réessayez plus tard.'
    } else {
      // Le backend renvoie normalement toujours 204 ici (anti-énumération).
      // Si une erreur arrive quand même, on ne distingue pas la cause pour
      // ne pas recréer une fuite d'information par un autre chemin.
      isDone.value = true
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="Mot de passe oublié" subtitle="Recevez un lien pour en choisir un nouveau.">
    <div v-if="isDone" class="space-y-4 text-sm text-ink">
      <p>
        Si un compte existe pour cette adresse, un email contenant un lien de réinitialisation
        vient d'être envoyé.
      </p>
      <RouterLink :to="{ name: 'login' }" class="text-wine hover:underline">Retour à la connexion</RouterLink>
    </div>

    <form v-else class="space-y-5" @submit.prevent="handleSubmit">
      <AuthTextField id="email" v-model="email" label="Email" type="email" autocomplete="email" />

      <p v-if="errorMessage" class="text-sm text-danger" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        :disabled="isSubmitting"
        class="w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper transition
               hover:bg-wine-hover disabled:opacity-60"
      >
        {{ isSubmitting ? 'Envoi...' : 'Envoyer le lien' }}
      </button>
    </form>

    <p v-if="!isDone" class="mt-6 text-sm text-ink/70">
      <RouterLink :to="{ name: 'login' }" class="text-wine hover:underline">Retour à la connexion</RouterLink>
    </p>
  </AuthLayout>
</template>
