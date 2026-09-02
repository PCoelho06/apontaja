<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import AuthTextField from '@/components/AuthTextField.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const token = typeof route.query.token === 'string' ? route.query.token : ''
const newPassword = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)
const isDone = ref(false)

async function handleSubmit() {
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await auth.resetPassword(token, newPassword.value)
    isDone.value = true
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : 'Impossible de réinitialiser le mot de passe.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="Nouveau mot de passe">
    <p v-if="!token" class="text-sm text-danger" role="alert">
      Lien invalide — merci de repartir depuis l'email reçu.
    </p>

    <div v-else-if="isDone" class="space-y-4 text-sm text-ink">
      <p>Votre mot de passe a été mis à jour. Vous pouvez maintenant vous connecter.</p>
      <RouterLink :to="{ name: 'login' }" class="text-wine hover:underline">Se connecter</RouterLink>
    </div>

    <form v-else class="space-y-5" @submit.prevent="handleSubmit">
      <AuthTextField
        id="newPassword"
        v-model="newPassword"
        label="Nouveau mot de passe"
        type="password"
        autocomplete="new-password"
      />

      <p v-if="errorMessage" class="text-sm text-danger" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        :disabled="isSubmitting"
        class="w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper transition
               hover:bg-wine-hover disabled:opacity-60"
      >
        {{ isSubmitting ? 'Enregistrement...' : 'Enregistrer' }}
      </button>
    </form>
  </AuthLayout>
</template>
