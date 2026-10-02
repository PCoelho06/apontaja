import { defineStore } from "pinia";

import { apiPatch, apiPost } from "@/lib/apiClient";
import { requireToken } from "@/lib/requireToken";
import type { AppointmentItem } from "@/stores/agenda";

/** startAt : instant ISO 8601 avec offset ; endAt est calculé par le back. */
export interface NewAppointment {
  customerProfileId: string;
  serviceId: string;
  resourceId: string;
  startAt: string;
}

function appointmentUrl(
  salonId: string,
  appointmentId: string,
  action: string,
): string {
  return `/api/salons/${salonId}/appointments/${appointmentId}/${action}`;
}

/**
 * Écritures sur les RDV. Après chaque appel, la vue recharge la journée de l'agenda.
 * Les 409 portent `issues[]` (disponibilité) ou un message (conflit, transition refusée).
 */
export const useAppointmentStore = defineStore("appointment", {
  actions: {
    createAppointment(salonId: string, payload: NewAppointment) {
      return apiPost<AppointmentItem>(
        `/api/salons/${salonId}/appointments`,
        payload,
        requireToken(),
      );
    },

    confirmAppointment(salonId: string, appointmentId: string) {
      return apiPatch<AppointmentItem>(
        appointmentUrl(salonId, appointmentId, "confirm"),
        undefined,
        requireToken(),
      );
    },

    completeAppointment(salonId: string, appointmentId: string) {
      return apiPatch<AppointmentItem>(
        appointmentUrl(salonId, appointmentId, "complete"),
        undefined,
        requireToken(),
      );
    },

    markNoShow(salonId: string, appointmentId: string) {
      return apiPatch<AppointmentItem>(
        appointmentUrl(salonId, appointmentId, "no-show"),
        undefined,
        requireToken(),
      );
    },

    cancelAppointment(
      salonId: string,
      appointmentId: string,
      reason: string | null,
    ) {
      return apiPatch<AppointmentItem>(
        appointmentUrl(salonId, appointmentId, "cancel"),
        reason ? { reason } : {},
        requireToken(),
      );
    },
  },
});
