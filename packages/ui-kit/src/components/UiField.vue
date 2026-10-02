<script setup lang="ts">
import { computed, useId } from "vue";

const props = withDefaults(
  defineProps<{ label: string; hint?: string; error?: string }>(),
  { hint: undefined, error: undefined },
);

defineSlots<{
  default(props: {
    id: string;
    "aria-describedby"?: string;
    "aria-invalid"?: "true";
  }): unknown;
}>();

const id = useId();
const hintId = `${id}-hint`;
const errorId = `${id}-error`;

const control = computed(() => {
  const describedBy = [
    props.error ? errorId : null,
    props.hint ? hintId : null,
  ]
    .filter((value) => value !== null)
    .join(" ");
  return {
    id,
    "aria-describedby": describedBy || undefined,
    "aria-invalid": props.error ? ("true" as const) : undefined,
  };
});
</script>

<template>
  <div>
    <label
      :for="id"
      class="block text-sm font-medium text-ink"
    >{{ label }}</label>
    <div class="mt-1.5">
      <slot v-bind="control" />
    </div>
    <p
      v-if="hint"
      :id="hintId"
      class="mt-1 text-xs text-ink/60"
    >
      {{ hint }}
    </p>
    <p
      v-if="error"
      :id="errorId"
      class="mt-1 text-xs text-danger"
    >
      {{ error }}
    </p>
  </div>
</template>
