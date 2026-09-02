<script setup lang="ts">
defineProps<{
  id: string
  label: string
  type?: string
  modelValue: string
  error?: string
  autocomplete?: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div>
    <label :for="id" class="block text-sm font-medium text-ink">{{ label }}</label>
    <input
      :id="id"
      :type="type ?? 'text'"
      :value="modelValue"
      :autocomplete="autocomplete"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? `${id}-error` : undefined"
      class="mt-1.5 block w-full rounded-md border border-border bg-white px-3 py-2 text-sm text-ink
             focus:border-wine focus:outline-none focus:ring-1 focus:ring-wine"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-sm text-danger">{{ error }}</p>
  </div>
</template>
