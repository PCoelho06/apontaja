<script setup lang="ts">
import { computed, ref, watch } from "vue";

import {
  UiAlert,
  UiBadge,
  UiButton,
  UiDialog,
  UiTextarea,
} from "@apontaja/ui-kit";

import { availableActions, canReschedule } from "@/lib/appointmentActions";
import { STATUS_LABELS, STATUS_TONES } from "@/lib/appointmentStatus";
import { formatCents, formatDuration } from "@/lib/catalogFormat";
import { errorMessage } from "@/lib/errorMessage";
import { formatInstant, instantToZonedLocal } from "@/lib/zonedTime";
import type { AppointmentItem } from "@/stores/agenda";
import { useAppointmentStore } from "@/stores/appointment";
import { useCustomerStore } from "@/stores/customer";
import { useResourceStore } from "@/stores/resource";
import { useSalonServiceStore } from "@/stores/salonService";
import AppointmentRescheduleForm from "@/components/agenda/AppointmentRescheduleForm.vue";

const props = defineProps<{
  salonId: string;
  timeZone: string;
  appointment: AppointmentItem | null;
}>();

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{ changed: []; rescheduled: [startAt: string] }>();

const appointmentStore = useAppointmentStore();
const customerStore = useCustomerStore();
const resourceStore = useResourceStore();
const serviceStore = useSalonServiceStore();

const busy = ref(false);
const error = ref<string | null>(null);
const cancelling = ref(false);
const rescheduling = ref(false);
const reason = ref("");

watch(
  () => [open.value, props.appointment?.appointmentId],
  () => {
    busy.value = false;
    error.value = null;
    cancelling.value = false;
    rescheduling.value = false;
    reason.value = "";
  },
);

const customer = computed(
  () =>
    customerStore.customers.find(
      (c) => c.customerProfileId === props.appointment?.customerProfileId,
    ) ?? null,
);
const resourceName = computed(
  () =>
    resourceStore.resources.find(
      (r) => r.resourceId === props.appointment?.resourceId,
    )?.name ?? "Ressource inconnue",
);
const serviceName = computed(
  () =>
    serviceStore.services.find(
      (s) => s.serviceId === props.appointment?.serviceId,
    )?.name ?? "Prestation supprimée",
);

const when = computed(() => {
  const appointment = props.appointment;
  if (!appointment) {
    return "";
  }
  const end = instantToZonedLocal(appointment.endAt, props.timeZone).slice(
    11,
    16,
  );
  return `${formatInstant(appointment.startAt, props.timeZone)} → ${end}`;
});

const actions = computed(() =>
  props.appointment
    ? availableActions(
        props.appointment.status,
        props.appointment.startAt,
        new Date(),
      )
    : [],
);

const canMove = computed(() =>
  props.appointment ? canReschedule(props.appointment.status) : false,
);

function startReschedule() {
  error.value = null;
  rescheduling.value = true;
}

function onRescheduled(startAt: string) {
  rescheduling.value = false;
  emit("changed");
  emit("rescheduled", startAt);
  open.value = false;
}

async function run(action: () => Promise<unknown>) {
  busy.value = true;
  error.value = null;
  try {
    await action();
    cancelling.value = false;
    reason.value = "";
    emit("changed");
  } catch (e) {
    error.value = errorMessage(e, "Action impossible.");
  } finally {
    busy.value = false;
  }
}

function confirm() {
  if (props.appointment) {
    const id = props.appointment.appointmentId;
    run(() => appointmentStore.confirmAppointment(props.salonId, id));
  }
}

function complete() {
  if (props.appointment) {
    const id = props.appointment.appointmentId;
    run(() => appointmentStore.completeAppointment(props.salonId, id));
  }
}

function markNoShow() {
  if (props.appointment) {
    const id = props.appointment.appointmentId;
    run(() => appointmentStore.markNoShow(props.salonId, id));
  }
}

function cancel() {
  if (props.appointment) {
    const id = props.appointment.appointmentId;
    const text = reason.value.trim();
    run(() =>
      appointmentStore.cancelAppointment(props.salonId, id, text || null),
    );
  }
}
</script>

<template>
  <UiDialog
    v-model:open="open"
    title="Rendez-vous"
  >
    <div
      v-if="appointment"
      class="space-y-4 text-sm text-ink"
    >
      <div class="flex items-center justify-between gap-3">
        <p class="font-medium">
          {{ when }}
        </p>
        <UiBadge :tone="STATUS_TONES[appointment.status]">
          {{ STATUS_LABELS[appointment.status] }}
        </UiBadge>
      </div>

      <dl class="grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5">
        <dt class="text-ink/60">
          Ressource
        </dt>
        <dd>{{ resourceName }}</dd>
        <dt class="text-ink/60">
          Client
        </dt>
        <dd>
          {{
            customer
              ? `${customer.firstName} ${customer.lastName}`
              : "Client inconnu"
          }}
          <span
            v-if="customer?.phone"
            class="block text-ink/60"
          >{{
            customer.phone
          }}</span>
          <span
            v-if="customer?.email"
            class="block text-ink/60"
          >{{
            customer.email
          }}</span>
        </dd>
        <dt class="text-ink/60">
          Prestation
        </dt>
        <dd>{{ serviceName }}</dd>
        <dt class="text-ink/60">
          Durée
        </dt>
        <dd>{{ formatDuration(appointment.durationAtBookingMinutes) }}</dd>
        <dt class="text-ink/60">
          Prix
        </dt>
        <dd>{{ formatCents(appointment.priceAtBookingCents) }}</dd>
      </dl>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>

      <div
        v-if="cancelling"
        class="space-y-3 rounded-md border border-danger/30 p-3"
      >
        <UiTextarea
          v-model="reason"
          label="Motif de l'annulation (optionnel)"
          maxlength="500"
          autofocus
        />
        <div class="flex justify-end gap-2">
          <UiButton
            variant="secondary"
            :disabled="busy"
            @click="cancelling = false"
          >
            Retour
          </UiButton>
          <UiButton
            variant="danger"
            :loading="busy"
            @click="cancel"
          >
            Confirmer l'annulation
          </UiButton>
        </div>
      </div>

      <AppointmentRescheduleForm
        v-else-if="rescheduling"
        :salon-id="salonId"
        :time-zone="timeZone"
        :appointment="appointment"
        @back="rescheduling = false"
        @done="onRescheduled"
      />

      <div
        v-else-if="actions.length > 0"
        class="flex flex-wrap justify-end gap-2"
      >
        <UiButton
          v-if="actions.includes('confirm')"
          :loading="busy"
          @click="confirm"
        >
          Confirmer
        </UiButton>
        <UiButton
          v-if="actions.includes('complete')"
          :loading="busy"
          @click="complete"
        >
          Terminer
        </UiButton>
        <UiButton
          v-if="canMove"
          variant="secondary"
          :disabled="busy"
          @click="startReschedule"
        >
          Déplacer
        </UiButton>
        <UiButton
          v-if="actions.includes('no-show')"
          variant="secondary"
          :disabled="busy"
          @click="markNoShow"
        >
          Client absent
        </UiButton>
        <UiButton
          v-if="actions.includes('cancel')"
          variant="danger"
          :disabled="busy"
          @click="cancelling = true"
        >
          Annuler le rendez-vous
        </UiButton>
      </div>
    </div>
  </UiDialog>
</template>
