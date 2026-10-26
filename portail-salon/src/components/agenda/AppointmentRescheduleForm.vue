<script setup lang="ts">
import { computed, ref } from "vue";

import { UiAlert, UiButton, UiInput } from "@apontaja/ui-kit";

import {
  describeLocalTimeError,
  describeRescheduleError,
  shouldReloadAfter,
} from "@/lib/appointmentErrors";
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

const emit = defineEmits<{
  back: [];
  done: [startAt: string];
  /** Le back a répondu 404/409 : la journée affichée est probablement périmée. */
  stale: [];
}>();

const store = useAppointmentStore();

const start = ref(instantToZonedLocal(props.appointment.startAt, props.timeZone));
const submitting = ref(false);
const formError = ref<string | null>(null);
const issues = ref<string[]>([]);

const hint = computed(() => {
  const kept = `Duração (${formatDuration(props.appointment.durationAtBookingMinutes)}) e preço mantidos.`;
  try {
    const startAt = zonedLocalToInstant(start.value, props.timeZone);
    const end = new Date(
      new Date(startAt).getTime() +
        props.appointment.durationAtBookingMinutes * 60_000,
    );
    return `Fim previsto: ${formatInstant(end.toISOString(), props.timeZone)}. ${kept}`;
  } catch {
    return kept;
  }
});

async function submit() {
  formError.value = null;
  issues.value = [];
  let startAt: string;
  try {
    startAt = zonedLocalToInstant(start.value, props.timeZone);
  } catch (e) {
    formError.value = describeLocalTimeError(e);
    return;
  }
  const unchanged =
    new Date(startAt).getTime() === new Date(props.appointment.startAt).getTime();
  if (unchanged) {
    formError.value = "Escolha uma hora de início diferente da atual.";
    return;
  }
  submitting.value = true;
  try {
    await store.rescheduleAppointment(
      props.salonId,
      props.appointment.appointmentId,
      startAt,
    );
    emit("done", startAt);
  } catch (e) {
    const failure = describeRescheduleError(e);
    issues.value = failure.issues;
    formError.value = failure.issues.length > 0 ? null : failure.message;
    if (shouldReloadAfter(e)) {
      emit("stale");
    }
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
      label="Nova hora de início"
      :hint="hint"
      autofocus
    />

    <UiAlert
      v-if="issues.length > 0"
      variant="error"
    >
      <p class="font-medium">
        Este horário não está disponível:
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
        Voltar
      </UiButton>
      <UiButton
        type="submit"
        :loading="submitting"
      >
        Mudar de hora
      </UiButton>
    </div>
  </form>
</template>



