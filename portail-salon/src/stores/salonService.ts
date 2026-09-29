import { defineStore } from "pinia";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/apiClient";
import { requireToken } from "@/lib/requireToken";

export interface SalonServiceItem {
  serviceId: string;
  salonId: string;
  name: string;
  description: string | null;
  defaultDurationMinutes: number;
  defaultPriceCents: number;
  createdAt: string;
}

export interface ServicePayload {
  name: string;
  description: string | null;
  defaultDurationMinutes: number;
  defaultPriceCents: number;
}

export interface ServiceResourceLink {
  resourceId: string;
  overridePriceCents: number | null;
  overrideDurationMinutes: number | null;
  effectivePriceCents: number;
  effectiveDurationMinutes: number;
}

/** null = utiliser la valeur par défaut de la prestation. */
export interface LinkOverrides {
  overridePriceCents: number | null;
  overrideDurationMinutes: number | null;
}

export const useSalonServiceStore = defineStore("salonService", {
  state: () => ({
    services: [] as SalonServiceItem[],
    loadedSalonId: null as string | null,
    links: {} as Record<string, ServiceResourceLink[]>,
  }),

  actions: {
    async fetchServices(salonId: string) {
      if (this.loadedSalonId !== salonId) {
        this.services = [];
        this.links = {};
      }
      this.services = await apiGet<SalonServiceItem[]>(
        `/api/salons/${salonId}/services`,
        requireToken(),
      );
      this.loadedSalonId = salonId;
    },

    async createService(salonId: string, payload: ServicePayload) {
      await apiPost<unknown>(
        `/api/salons/${salonId}/services`,
        payload,
        requireToken(),
      );
      await this.fetchServices(salonId);
    },

    async updateService(
      salonId: string,
      serviceId: string,
      payload: ServicePayload,
    ) {
      await apiPut<unknown>(
        `/api/salons/${salonId}/services/${serviceId}`,
        payload,
        requireToken(),
      );
      await this.fetchServices(salonId);
    },

    async removeService(salonId: string, serviceId: string) {
      await apiDelete<void>(
        `/api/salons/${salonId}/services/${serviceId}`,
        requireToken(),
      );
      delete this.links[serviceId];
      await this.fetchServices(salonId);
    },

    async fetchLinks(salonId: string, serviceId: string) {
      this.links[serviceId] = await apiGet<ServiceResourceLink[]>(
        `/api/salons/${salonId}/services/${serviceId}/resources`,
        requireToken(),
      );
    },

    async saveLink(
      salonId: string,
      serviceId: string,
      resourceId: string,
      overrides: LinkOverrides,
    ) {
      await apiPut<unknown>(
        `/api/salons/${salonId}/services/${serviceId}/resources/${resourceId}`,
        overrides,
        requireToken(),
      );
      await this.fetchLinks(salonId, serviceId);
    },

    async removeLink(salonId: string, serviceId: string, resourceId: string) {
      await apiDelete<void>(
        `/api/salons/${salonId}/services/${serviceId}/resources/${resourceId}`,
        requireToken(),
      );
      await this.fetchLinks(salonId, serviceId);
    },
  },
});
