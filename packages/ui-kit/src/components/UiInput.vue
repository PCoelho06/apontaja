<script setup lang="ts">
import { controlClass } from "./fieldStyles";
import UiField from "./UiField.vue";

defineOptions({ inheritAttrs: false });

withDefaults(
  defineProps<{ label: string; type?: string; hint?: string; error?: string }>(),
  { type: "text", hint: undefined, error: undefined },
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
    <input
      v-bind="{ ...$attrs, ...control }"
      v-model="model"
      :type="type"
      :class="controlClass"
    >
  </UiField>
</template>
