<script setup lang="ts">
import { computed } from "vue";

type Variant = "error" | "info" | "success";

const VARIANT_CLASSES: Record<Variant, string> = {
  error: "border-danger/30 bg-danger/5 text-danger",
  info: "border-border bg-white text-ink",
  success: "border-brass/40 bg-brass/10 text-ink",
};

const props = withDefaults(defineProps<{ variant?: Variant }>(), {
  variant: "info",
});

const role = computed(() => (props.variant === "error" ? "alert" : "status"));
</script>

<template>
  <div
    :role="role"
    :class="['rounded-md border px-3 py-2 text-sm', VARIANT_CLASSES[variant]]"
  >
    <slot />
  </div>
</template>
