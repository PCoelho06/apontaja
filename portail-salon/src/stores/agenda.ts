import { defineStore } from "pinia";

import { apiGet } from "@/lib/apiClient";
import { dayBounds } from "@/lib/agenda";
import type { AppointmentStatus } from "@/lib/appointmentStatus";
import { requireToken } from "@/lib/requireToken";
import type { ScheduleSlot } from "@/lib/schedule";
import { useClosureStore, type SalonClosure } from "@/stores/closure";
import { useScheduleStore } from "@/stores/schedule";

export interface AppointmentItem {
  appointmentId: string;
  salonId: string;
  customerProfileId: string;
  serviceId: string;
  resourceId: string;
  startAt: string;
  endAt: string;
  status: AppointmentStatus;
  priceAtBookingCents: number;
  durationAtBookingMinutes: number;
  createdAt: string;
}

// Ne garde que le résultat de la dernière demande (navigation rapide entre les jours).
let latestRequest = 0;

export const useAgendaStore = defineStore("agenda", {
  state: () => ({
    appointments: [] as AppointmentItem[],
    salonSlots: [] as ScheduleSlot[],
    resourceSlots: {} as Record<string, ScheduleSlot[]>,
    closures: [] as SalonClosure[],
  }),

  actions: {
    /**
     * Charge une journée (heure du salon) : RDV de toutes les ressources, horaires et
     * fermetures du salon et de chaque ressource. Tout est appliqué d'un coup.
     */
    async loadDay(
      salonId: string,
      date: string,
      timeZone: string,
      resourceIds: string[],
    ) {
      const request = ++latestRequest;
      const { from, to } = dayBounds(date, timeZone);
      const query = `from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
      const schedules = useScheduleStore();
      const closureStore = useClosureStore();
      const token = requireToken();

      const [appointments, salonSlots, salonClosures, perResource] =
        await Promise.all([
          apiGet<AppointmentItem[]>(
            `/api/salons/${salonId}/appointments?${query}`,
            token,
          ),
          schedules.fetchSchedule(salonId, null),
          closureStore.listOverlapping(salonId, null, from, to),
          Promise.all(
            resourceIds.map(async (resourceId) => {
              const [slots, closures] = await Promise.all([
                schedules.fetchSchedule(salonId, resourceId),
                closureStore.listOverlapping(salonId, resourceId, from, to),
              ]);
              return { resourceId, slots, closures };
            }),
          ),
        ]);

      if (request !== latestRequest) {
        return;
      }
      this.appointments = appointments;
      this.salonSlots = salonSlots;
      this.resourceSlots = Object.fromEntries(
        perResource.map((entry) => [entry.resourceId, entry.slots]),
      );
      this.closures = [
        ...salonClosures,
        ...perResource.flatMap((entry) => entry.closures),
      ];
    },
  },
});
