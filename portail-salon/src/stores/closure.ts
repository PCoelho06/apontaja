import { defineStore } from "pinia";

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/apiClient";
import { requireToken } from "@/lib/requireToken";

export interface SalonClosure {
  closureId: string;
  salonId: string;
  resourceId: string | null;
  startAt: string;
  endAt: string;
  reason: string | null;
  createdAt: string;
}

/** startAt/endAt : instants ISO 8601 avec offset. */
export interface ClosurePayload {
  startAt: string;
  endAt: string;
  reason: string | null;
}

function closuresUrl(salonId: string, resourceId: string | null): string {
  return resourceId
    ? `/api/salons/${salonId}/resources/${resourceId}/closures`
    : `/api/salons/${salonId}/closures`;
}

export const useClosureStore = defineStore("closure", {
  state: () => ({
    closures: [] as SalonClosure[],
  }),

  actions: {
    /** `from` (ISO) : ne garde que les fermetures qui se terminent après cet instant. */
    async fetchClosures(
      salonId: string,
      resourceId: string | null,
      from?: string,
    ) {
      const query = from ? `?from=${encodeURIComponent(from)}` : "";
      this.closures = await apiGet<SalonClosure[]>(
        `${closuresUrl(salonId, resourceId)}${query}`,
        requireToken(),
      );
    },

    /** Fermetures recouvrant [from, to) d'un périmètre, sans modifier l'état du store. */
    async listOverlapping(
      salonId: string,
      resourceId: string | null,
      from: string,
      to: string,
    ): Promise<SalonClosure[]> {
      const query = `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
      return apiGet<SalonClosure[]>(
        `${closuresUrl(salonId, resourceId)}${query}`,
        requireToken(),
      );
    },

    /** 409 si la période recouvre des RDV actifs (voir parseClosureConflict). */
    async createClosure(
      salonId: string,
      resourceId: string | null,
      payload: ClosurePayload,
    ) {
      await apiPost<unknown>(
        closuresUrl(salonId, resourceId),
        payload,
        requireToken(),
      );
    },

    async updateClosure(
      salonId: string,
      resourceId: string | null,
      closureId: string,
      payload: ClosurePayload,
    ) {
      await apiPut<unknown>(
        `${closuresUrl(salonId, resourceId)}/${closureId}`,
        payload,
        requireToken(),
      );
    },

    async removeClosure(
      salonId: string,
      resourceId: string | null,
      closureId: string,
    ) {
      await apiDelete<void>(
        `${closuresUrl(salonId, resourceId)}/${closureId}`,
        requireToken(),
      );
    },
  },
});
