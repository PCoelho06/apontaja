<script setup lang="ts">
import { computed } from "vue";

import {
  formatMinutes,
  type AgendaColumn,
  type Interval,
} from "@/lib/agenda";
import { STATUS_BLOCK_CLASSES, STATUS_LABELS } from "@/lib/appointmentStatus";

const HOUR_HEIGHT = 56; // px
const PX_PER_MINUTE = HOUR_HEIGHT / 60;

const props = defineProps<{ columns: AgendaColumn[]; axis: Interval }>();

const gridHeight = computed(
  () => `${(props.axis.end - props.axis.start) * PX_PER_MINUTE}px`,
);

const hours = computed(() => {
  const marks: number[] = [];
  for (let minute = props.axis.start; minute <= props.axis.end; minute += 60) {
    marks.push(minute);
  }
  return marks;
});

function top(minute: number): string {
  return `${(minute - props.axis.start) * PX_PER_MINUTE}px`;
}

function height(interval: Interval): string {
  return `${(interval.end - interval.start) * PX_PER_MINUTE}px`;
}

function blockStyle(interval: Interval & { lane?: number; lanes?: number }) {
  const lanes = interval.lanes ?? 1;
  const lane = interval.lane ?? 0;
  return {
    top: top(interval.start),
    height: height(interval),
    left: `${(lane / lanes) * 100}%`,
    width: `${100 / lanes}%`,
  };
}
</script>

<template>
  <div class="overflow-x-auto rounded-md border border-border bg-white">
    <div class="flex min-w-max">
      <!-- Échelle des heures -->
      <div class="w-14 shrink-0">
        <div class="h-10 border-b border-border" />
        <div
          class="relative"
          :style="{ height: gridHeight }"
        >
          <span
            v-for="minute in hours"
            :key="minute"
            class="absolute right-2 -translate-y-1/2 text-xs text-ink/50"
            :style="{ top: top(minute) }"
          >{{ formatMinutes(minute) }}</span>
        </div>
      </div>

      <!-- Une colonne par ressource -->
      <div
        v-for="column in columns"
        :key="column.resourceId"
        class="min-w-44 flex-1 border-l border-border"
      >
        <div
          class="flex h-10 items-center justify-center border-b border-border px-2 text-sm font-medium text-ink"
        >
          {{ column.name }}
        </div>
        <div
          class="relative bg-ink/5"
          :style="{ height: gridHeight }"
        >
          <!-- Créneaux ouverts à la réservation -->
          <div
            v-for="(interval, index) in column.available"
            :key="`open-${index}`"
            class="absolute inset-x-0 bg-white"
            :style="{ top: top(interval.start), height: height(interval) }"
          />

          <!-- Lignes d'heure -->
          <div
            v-for="minute in hours"
            :key="`line-${minute}`"
            class="absolute inset-x-0 border-t border-border/60"
            :style="{ top: top(minute) }"
          />

          <!-- Fermetures -->
          <div
            v-for="(closure, index) in column.closures"
            :key="`closure-${index}`"
            class="absolute inset-x-0 bg-danger/10 px-1 text-xs text-danger"
            :style="{ top: top(closure.start), height: height(closure) }"
            :title="closure.reason ?? 'Fermeture'"
          >
            Fermé{{ closure.reason ? ` · ${closure.reason}` : "" }}
          </div>

          <!-- Rendez-vous -->
          <div
            v-for="appointment in column.appointments"
            :key="appointment.appointmentId"
            class="absolute overflow-hidden rounded border-l-4 px-1.5 py-0.5 text-xs"
            :class="STATUS_BLOCK_CLASSES[appointment.status]"
            :style="blockStyle(appointment)"
            :title="`${formatMinutes(appointment.start)}–${formatMinutes(appointment.end)} · ${appointment.customerName} · ${appointment.serviceName} · ${STATUS_LABELS[appointment.status]}`"
          >
            <p class="truncate font-medium">
              {{ formatMinutes(appointment.start) }}–{{
                formatMinutes(appointment.end)
              }}
              {{ appointment.customerName }}
            </p>
            <p class="truncate">
              {{ appointment.serviceName }}
            </p>
            <p
              v-if="appointment.status !== 'CONFIRMED'"
              class="truncate"
            >
              {{ STATUS_LABELS[appointment.status] }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
