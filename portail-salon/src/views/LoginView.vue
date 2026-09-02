<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import AuthTextField from '@/components/AuthTextField.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const errorMessage = ref('')
const isSubmitting = ref(false)

async function handleSubmit() {
  errorMessage.value = ''
  isSubmitting.value = true
  try {
    await auth.login(email.value, password.value)
    await router.push({ name: 'home' })
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : 'Impossible de se connecter, réessayez.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="Se connecter" subtitle="Accédez à l'espace de gestion de votre salon.">
    <form class="space-y-5" @submit.prevent="handleSubmit">
      <AuthTextField id="email" v-model="email" label="Email" type="email" autocomplete="email" />
      <AuthTextField
        id="password"
        v-model="password"
        label="Mot de passe"
        type="password"
        autocomplete="current-password"
      />

      <p v-if="errorMessage" class="text-sm text-danger" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        :disabled="isSubmitting"
        class="w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper transition
               hover:bg-wine-hover disabled:opacity-60"
      >
        {{ isSubmitting ? 'Connexion...' : 'Se connecter' }}
      </button>
    </form>

    <p class="mt-6 text-sm text-ink/70">
      <RouterLink :to="{ name: 'forgot-password' }" class="text-wine hover:underline">
        Mot de passe oublié ?
      </RouterLink>
    </p>
    <p class="mt-2 text-sm text-ink/70">
      Pas encore de compte ?
      <RouterLink :to="{ name: 'register' }" class="text-wine hover:underline">Créer un compte</RouterLink>
    </p>
  </AuthLayout>
</template>
