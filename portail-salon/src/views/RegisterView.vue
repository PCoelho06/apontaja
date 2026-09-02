<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import AuthTextField from '@/components/AuthTextField.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const email = ref('')
const password = ref('')

const errorMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})
const isSubmitting = ref(false)
const isDone = ref(false)

const passwordHint = computed(() =>
  password.value.length > 0 && password.value.length < 12
    ? `Encore ${12 - password.value.length} caractère(s) minimum.`
    : '',
)

async function handleSubmit() {
  errorMessage.value = ''
  fieldErrors.value = {}
  isSubmitting.value = true
  try {
    await auth.register(email.value, password.value)
    isDone.value = true
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value = error.message
      fieldErrors.value = error.fieldErrors ?? {}
    } else {
      errorMessage.value = 'Impossible de créer le compte, réessayez.'
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="Créer un compte" subtitle="Quelques informations pour démarrer.">
    <div v-if="isDone" class="space-y-4">
      <p class="text-sm text-ink">
        Compte créé. Un email de confirmation vient de vous être envoyé — vérifiez votre boîte de
        réception, puis connectez-vous.
      </p>
      <RouterLink
        :to="{ name: 'login' }"
        class="inline-block rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper hover:bg-wine-hover"
      >
        Aller à la connexion
      </RouterLink>
    </div>

    <form v-else class="space-y-5" @submit.prevent="handleSubmit">
      <AuthTextField
        id="email"
        v-model="email"
        label="Email"
        type="email"
        autocomplete="email"
        :error="fieldErrors.email"
      />
      <AuthTextField
        id="password"
        v-model="password"
        label="Mot de passe"
        type="password"
        autocomplete="new-password"
        :error="fieldErrors.password || passwordHint"
      />

      <p v-if="errorMessage" class="text-sm text-danger" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        :disabled="isSubmitting"
        class="w-full rounded-md bg-wine px-4 py-2.5 text-sm font-medium text-paper transition
               hover:bg-wine-hover disabled:opacity-60"
      >
        {{ isSubmitting ? 'Création...' : 'Créer mon compte' }}
      </button>

      <p class="text-xs text-ink/60">
        En créant un compte, vous reconnaissez avoir pris connaissance des conditions générales
        d'utilisation et de la politique de confidentialité, et les acceptez.
      </p>
    </form>

    <p v-if="!isDone" class="mt-6 text-sm text-ink/70">
      Déjà un compte ?
      <RouterLink :to="{ name: 'login' }" class="text-wine hover:underline">Se connecter</RouterLink>
    </p>
  </AuthLayout>
</template>
