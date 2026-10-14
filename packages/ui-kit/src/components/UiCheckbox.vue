<script setup lang="ts">
import { useId } from "vue";

defineOptions({ inheritAttrs: false });

withDefaults(defineProps<{ label: string; hint?: string }>(), {
  hint: undefined,
});

const model = defineModel<boolean>({ default: false });
const hintId = useId();
</script>

<template>
  <div>
    <label class="flex items-center gap-2 text-sm text-ink">
      <input
        v-bind="$attrs"
        v-model="model"
        type="checkbox"
        :aria-describedby="hint ? hintId : undefined"
        class="size-4 rounded border-border accent-wine focus:outline-none focus-visible:ring-2 focus-visible:ring-wine disabled:cursor-not-allowed disabled:opacity-50"
      >
      {{ label }}
    </label>
    <p
      v-if="hint"
      :id="hintId"
      class="mt-1 pl-6 text-xs text-ink/60"
    >
      {{ hint }}
    </p>
  </div>
</template>
