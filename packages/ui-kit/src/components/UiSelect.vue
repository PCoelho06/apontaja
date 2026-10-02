<script setup lang="ts">
import { controlClass } from "./fieldStyles";
import UiField from "./UiField.vue";

defineOptions({ inheritAttrs: false });

withDefaults(
  defineProps<{ label: string; hint?: string; error?: string }>(),
  { hint: undefined, error: undefined },
);

const model = defineModel<string>({ default: "" });
</script>

<template>
  <UiField
    v-slot="control"
    :label="label"
    :hint="hint"
    :error="error"
  >
    <select
      v-bind="{ ...$attrs, ...control }"
      v-model="model"
      :class="controlClass"
    >
      <slot />
    </select>
  </UiField>
</template>
