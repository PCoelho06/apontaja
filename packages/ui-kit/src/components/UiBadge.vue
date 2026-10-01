<script setup lang="ts">
type Tone = "neutral" | "wine" | "brass" | "danger";

const TONE_CLASSES: Record<Tone, string> = {
  neutral: "bg-ink/5 text-ink/70",
  wine: "bg-wine/10 text-wine",
  brass: "bg-brass/15 text-ink",
  danger: "bg-danger/10 text-danger",
};

withDefaults(defineProps<{ tone?: Tone }>(), { tone: "neutral" });
</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
      TONE_CLASSES[tone],
    ]"
  >
    <slot />
  </span>
</template>
