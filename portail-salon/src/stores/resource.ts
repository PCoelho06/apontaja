import { defineStore } from "pinia";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/apiClient";
import { requireToken } from "@/lib/requireToken";

export type ResourceType = "EMPLOYEE" | "MACHINE";

export interface SalonResource {
  resourceId: string;
  salonId: string;
  name: string;
  type: ResourceType;
  staffMembershipId: string | null;
  createdAt: string;
}

/** PUT = remplacement complet : staffMembershipId null délie la ressource. */
export interface ResourcePayload {
  name: string;
  type: ResourceType;
  staffMembershipId: string | null;
}

export const useResourceStore = defineStore("resource", {
  state: () => ({
    resources: [] as SalonResource[],
    loadedSalonId: null as string | null,
  }),

  actions: {
    async fetchResources(salonId: string) {
      if (this.loadedSalonId !== salonId) {
        this.resources = [];
      }
      this.resources = await apiGet<SalonResource[]>(
        `/api/salons/${salonId}/resources`,
        requireToken(),
      );
      this.loadedSalonId = salonId;
    },

    async createResource(salonId: string, payload: ResourcePayload) {
      await apiPost<unknown>(
        `/api/salons/${salonId}/resources`,
        payload,
        requireToken(),
      );
      await this.fetchResources(salonId);
    },

    async updateResource(
      salonId: string,
      resourceId: string,
      payload: ResourcePayload,
    ) {
      await apiPut<unknown>(
        `/api/salons/${salonId}/resources/${resourceId}`,
        payload,
        requireToken(),
      );
      await this.fetchResources(salonId);
    },

    async removeResource(salonId: string, resourceId: string) {
      await apiDelete<void>(
        `/api/salons/${salonId}/resources/${resourceId}`,
        requireToken(),
      );
      await this.fetchResources(salonId);
    },
  },
});
