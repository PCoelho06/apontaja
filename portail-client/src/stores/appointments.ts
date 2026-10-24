import { defineStore } from "pinia";

import { apiGet, apiPatch } from "@apontaja/web-core/api-client";
import { requireToken } from "@apontaja/web-core/require-token";

import type {
  AppointmentScope,
  ClientAppointment,
  ClientAppointmentPage,
} from "@/lib/myAppointments";
import {
  mergeAppointments,
  PAGE_SIZE,
  replaceAppointment,
} from "@/lib/myAppointments";

/** Un nouveau chargement invalide toute réponse encore en vol : la dernière demande gagne. */
let requestId = 0;

function listPath(scope: AppointmentScope, page: number): string {
  return `/api/me/appointments?scope=${scope}&page=${page}&size=${PAGE_SIZE}`;
}

export const useAppointmentsStore = defineStore("appointments", {
  state: () => ({
    scope: "upcoming" as AppointmentScope,
    items: [] as ClientAppointment[],
    page: 0,
    totalPages: 0,
    totalElements: 0,
  }),
  getters: {
    hasMore: (state) => state.page + 1 < state.totalPages,
  },
  actions: {
    /** Charge la première page d'un onglet (la liste précédente est vidée). */
    async load(scope: AppointmentScope): Promise<void> {
      requestId += 1;
      const id = requestId;
      this.scope = scope;
      this.items = [];
      this.page = 0;
      this.totalPages = 0;
      this.totalElements = 0;
      const result = await apiGet<ClientAppointmentPage>(
        listPath(scope, 0),
        requireToken(),
      );
      if (id !== requestId) {
        return;
      }
      this.items = result.content;
      this.page = result.page;
      this.totalPages = result.totalPages;
      this.totalElements = result.totalElements;
    },

    /** Ajoute la page suivante de l'onglet courant. */
    async loadMore(): Promise<void> {
      if (!this.hasMore) {
        return;
      }
      const id = requestId;
      const result = await apiGet<ClientAppointmentPage>(
        listPath(this.scope, this.page + 1),
        requireToken(),
      );
      if (id !== requestId) {
        return;
      }
      this.items = mergeAppointments(this.items, result.content);
      this.page = result.page;
      this.totalPages = result.totalPages;
      this.totalElements = result.totalElements;
    },

    /** Annule un rendez-vous ; la ligne est mise à jour en place (elle ne change pas d'onglet). */
    async cancel(appointmentId: string): Promise<ClientAppointment> {
      const updated = await apiPatch<ClientAppointment>(
        `/api/me/appointments/${encodeURIComponent(appointmentId)}/cancel`,
        undefined,
        requireToken(),
      );
      this.items = replaceAppointment(this.items, updated);
      return updated;
    },
  },
});
