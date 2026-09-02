<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import AuthLayout from '@/layouts/AuthLayout.vue'
import { ApiError } from '@/lib/apiClient'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const status = ref<'pending' | 'success' | 'error'>('pending')
const errorMessage = ref('')

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    status.value = 'error'
    errorMessage.value = 'Lien invalide.'
    return
  }

  try {
    await auth.confirmEmail(token)
    status.value = 'success'
  } catch (error) {
    status.value = 'error'
    errorMessage.value = error instanceof ApiError ? error.message : "Impossible de confirmer l'email."
  }
})
</script>

<template>
  <AuthLayout title="Confirmation de l'email">
    <p
      v-if="status === 'pending'"
      class="text-sm text-ink"
    >
      Vérification en cours...
    </p>
    <p
      v-else-if="status === 'success'"
      class="text-sm text-ink"
    >
      Votre email est confirmé.
    </p>
    <p
      v-else
      class="text-sm text-danger"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <RouterLink
      :to="{ name: 'login' }"
      class="mt-6 inline-block text-sm text-wine hover:underline"
    >
      Retour à la connexion
    </RouterLink>
  </AuthLayout>
</template>
