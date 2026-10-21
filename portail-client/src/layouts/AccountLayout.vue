<template>
  <div class="min-h-screen bg-paper">
    <header class="border-b border-border">
      <div class="mx-auto flex max-w-2xl items-center justify-between px-6 py-4">
        <p class="font-display text-xl text-wine">
          Apontaja
        </p>
        <slot name="actions" />
      </div>
    </header>

    <main class="mx-auto max-w-2xl px-6 py-10">
      <slot />
    </main>
  </div>
</template>
