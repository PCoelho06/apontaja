<script setup lang="ts">
import { onMounted, useId, useTemplateRef, watch } from "vue";

defineProps<{ title: string }>();

const open = defineModel<boolean>("open", { default: false });

const dialog = useTemplateRef("dialogElement");
const titleId = useId();

function sync(isOpen: boolean) {
  const element = dialog.value;
  if (!element) {
    return;
  }
  if (isOpen && !element.open) {
    element.showModal();
  } else if (!isOpen && element.open) {
    element.close();
  }
}

onMounted(() => sync(open.value));
// flush "post" : le corps est déjà rendu quand showModal() choisit l'élément à focaliser
watch(open, sync, { flush: "post" });
</script>

<!--
  <dialog> natif : focus piégé, Échap et fond (::backdrop) gérés par le navigateur.
  Pour focaliser un champ à l'ouverture, mettre `autofocus` dessus.
  Le corps n'est rendu que lorsque la boîte est ouverte (un formulaire repart de zéro).
-->
<template>
  <dialog
    ref="dialogElement"
    :aria-labelledby="titleId"
    class="m-auto w-full max-w-lg rounded-lg border border-border bg-paper p-0 text-ink shadow-xl backdrop:bg-ink/40"
    @close="open = false"
    @click.self="open = false"
  >
    <div class="flex flex-col gap-4 p-6">
      <header class="flex items-start justify-between gap-4">
        <h2
          :id="titleId"
          class="font-display text-lg text-ink"
        >
          {{ title }}
        </h2>
        <button
          type="button"
          aria-label="Fermer"
          class="rounded-md px-2 text-xl leading-none text-ink/60 hover:text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-wine"
          @click="open = false"
        >
          ×
        </button>
      </header>
      <div v-if="open">
        <slot />
      </div>
      <footer
        v-if="open && $slots.footer"
        class="flex justify-end gap-2"
      >
        <slot name="footer" />
      </footer>
    </div>
  </dialog>
</template>
