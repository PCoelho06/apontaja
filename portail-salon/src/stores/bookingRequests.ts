import { defineStore } from "pinia";

import { apiGet } from "@apontaja/web-core/api-client";
import { requireToken } from "@apontaja/web-core/require-token";
import {
  DECLINE_REASON,
  REQUEST_WINDOW_DAYS,
  pendingRequests,
} from "@/lib/bookingRequests";
import { useAppointmentStore } from "@/stores/appointment";
import type { AppointmentItem } from "@/stores/agenda";

// Ne garde que le résultat de la dernière demande (actualisations rapprochées).
let latestRequest = 0;

/**
 * Demandes de réservation en ligne à valider (RDV `SCHEDULED` à venir). Pas de polling : la vue charge à
 * l'ouverture et sur demande. Confirmer ou refuser retire la demande de la liste ; en cas d'erreur (409 : le
 * client a annulé entre-temps), la vue recharge.
 */
export const useBookingRequestsStore = defineStore("bookingRequests", {
  state: () => ({
    requests: [] as AppointmentItem[],
    loadedSalonId: null as string | null,
  }),

  getters: {
    count: (state): number => state.requests.length,
  },

  actions: {
    async fetchRequests(salonId: string, now: Date = new Date()) {
      const request = ++latestRequest;
      if (this.loadedSalonId !== salonId) {
        this.requests = [];
      }
      const to = new Date(now.getTime() + REQUEST_WINDOW_DAYS * 86_400_000);
      const query =
        `status=SCHEDULED&from=${encodeURIComponent(now.toISOString())}` +
        `&to=${encodeURIComponent(to.toISOString())}`;
      const items = await apiGet<AppointmentItem[]>(
        `/api/salons/${salonId}/appointments?${query}`,
        requireToken(),
      );
      if (request !== latestRequest) {
        return;
      }
      this.requests = pendingRequests(items, now);
      this.loadedSalonId = salonId;
    },

    async confirmRequest(salonId: string, appointmentId: string) {
      await useAppointmentStore().confirmAppointment(salonId, appointmentId);
      this.remove(appointmentId);
    },

    async declineRequest(salonId: string, appointmentId: string) {
      await useAppointmentStore().cancelAppointment(
        salonId,
        appointmentId,
        DECLINE_REASON,
      );
      this.remove(appointmentId);
    },

    remove(appointmentId: string) {
      this.requests = this.requests.filter(
        (item) => item.appointmentId !== appointmentId,
      );
    },
  },
});
