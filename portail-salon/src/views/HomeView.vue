<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function handleLogout() {
  try {
    await auth.logout()
  } finally {
    await router.push({ name: 'login' })
  }
}
</script>

<template>
  <main class="flex min-h-screen flex-col items-center justify-center gap-4 bg-paper px-6 text-center">
    <p class="font-display text-2xl text-ink">
      Bienvenue, {{ auth.account?.email }}
    </p>
    <p class="text-sm text-ink/70">
      Le reste du portail salon arrive dans les prochaines phases.
    </p>
    <button
      type="button"
      class="rounded-md border border-wine px-4 py-2 text-sm font-medium text-wine transition
             hover:bg-wine hover:text-paper"
      @click="handleLogout"
    >
      Se déconnecter
    </button>
  </main>
</template>
