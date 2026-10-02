<script setup lang="ts">
import { computed, ref } from "vue";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

import { describeIssues } from "@/lib/availabilityIssues";
import { formatDuration } from "@/lib/catalogFormat";
import {
  formatInstant,
  instantToZonedLocal,
  zonedLocalToInstant,
} from "@/lib/zonedTime";
import type { AppointmentItem } from "@/stores/agenda";
import { useAppointmentStore } from "@/stores/appointment";

const props = defineProps<{
  salonId: string;
  timeZone: string;
  appointment: AppointmentItem;
}>();

const emit = defineEmits<{ back: []; done: [startAt: string] }>();

const store = useAppointmentStore();

const start = ref(instantToZonedLocal(props.appointment.startAt, props.timeZone));
const submitting = ref(false);
const formError = ref<string | null>(null);
const issues = ref<string[]>([]);

const hint = computed(() => {
  const kept = `Durée (${formatDuration(props.appointment.durationAtBookingMinutes)}) et prix conservés.`;
  try {
    const startAt = zonedLocalToInstant(start.value, props.timeZone);
    const end = new Date(
      new Date(startAt).getTime() +
        props.appointment.durationAtBookingMinutes * 60_000,
    );
    return `Fin prévue : ${formatInstant(end.toISOString(), props.timeZone)}. ${kept}`;
  } catch {
    return kept;
  }
});

async function submit() {
  formError.value = null;
  issues.value = [];
  try {
    const startAt = zonedLocalToInstant(start.value, props.timeZone);
    if (new Date(startAt).getTime() === new Date(props.appointment.startAt).getTime()) {
      formError.value = "Choisissez un début différent de l'actuel.";
      return;
    }
    submitting.value = true;
    await store.rescheduleAppointment(
      props.salonId,
      props.appointment.appointmentId,
      startAt,
    );
    emit("done", startAt);
  } catch (e) {
    issues.value = describeIssues(e);
    formError.value =
      issues.value.length > 0
        ? null
        : e instanceof Error
          ? e.message
          : "Déplacement impossible.";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <form
    class="space-y-3 rounded-md border border-border p-3"
    @submit.prevent="submit"
  >
    <UiInput
      v-model="start"
      type="datetime-local"
      label="Nouveau début"
      :hint="hint"
      autofocus
    />

    <UiAlert
      v-if="issues.length > 0"
      variant="error"
    >
      <p class="font-medium">
        Ce créneau n'est pas disponible :
      </p>
      <ul class="list-disc pl-5">
        <li
          v-for="issue in issues"
          :key="issue"
        >
          {{ issue }}
        </li>
      </ul>
    </UiAlert>
    <UiAlert
      v-else-if="formError"
      variant="error"
    >
      {{ formError }}
    </UiAlert>

    <div class="flex justify-end gap-2">
      <UiButton
        variant="secondary"
        :disabled="submitting"
        @click="emit('back')"
      >
        Retour
      </UiButton>
      <UiButton
        type="submit"
        :loading="submitting"
      >
        Déplacer
      </UiButton>
    </div>
  </form>
</template>
