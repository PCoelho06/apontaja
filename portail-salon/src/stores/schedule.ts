import { defineStore } from "pinia";

import { apiGet, apiPut } from "@/lib/apiClient";
import { requireToken } from "@/lib/requireToken";
import { normalizeSlot, type ScheduleSlot } from "@/lib/schedule";

interface ScheduleResponse {
  slots: ScheduleSlot[];
}

function scheduleUrl(salonId: string, resourceId: string | null): string {
  return resourceId
    ? `/api/salons/${salonId}/resources/${resourceId}/schedule`
    : `/api/salons/${salonId}/schedule`;
}

/**
 * Horaires hebdomadaires du salon (resourceId null) ou d'une ressource.
 * Sans état : la vue garde sa copie éditable. PUT = remplacement complet de la semaine.
 */
export const useScheduleStore = defineStore("schedule", {
  actions: {
    async fetchSchedule(
      salonId: string,
      resourceId: string | null,
    ): Promise<ScheduleSlot[]> {
      const response = await apiGet<ScheduleResponse>(
        scheduleUrl(salonId, resourceId),
        requireToken(),
      );
      return response.slots.map(normalizeSlot);
    },

    async saveSchedule(
      salonId: string,
      resourceId: string | null,
      slots: ScheduleSlot[],
    ): Promise<ScheduleSlot[]> {
      await apiPut<unknown>(
        scheduleUrl(salonId, resourceId),
        { slots },
        requireToken(),
      );
      return this.fetchSchedule(salonId, resourceId);
    },
  },
});
