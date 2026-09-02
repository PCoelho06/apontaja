<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <div class="flex min-h-screen flex-col md:flex-row">
    <aside class="flex flex-col justify-center bg-wine px-8 py-12 text-paper md:w-2/5 md:px-16">
      <p class="font-display text-4xl">Apontaja</p>
      <p class="mt-4 max-w-sm text-sm text-paper/80">
        La gestion de rendez-vous pensée pour les salons de coiffure et de beauté.
      </p>
    </aside>

    <main class="flex flex-1 items-center justify-center bg-paper px-6 py-12">
      <div class="w-full max-w-sm">
        <h1 class="font-display text-2xl text-ink">{{ title }}</h1>
        <p v-if="subtitle" class="mt-2 text-sm text-ink/70">{{ subtitle }}</p>

        <div class="mt-8">
          <slot />
        </div>
      </div>
    </main>
  </div>
</template>
