import { defineStore } from "pinia";

import { apiGet, apiPost } from "@apontaja/web-core/api-client";
import { requireToken } from "@apontaja/web-core/require-token";

import type {
  BookingRequest,
  OnlineBookingResult,
  PublicOffering,
  PublicSalon,
  PublicSlots,
} from "@/lib/publicBooking";

function publicPath(salonId: string): string {
  return `/api/public/salons/${encodeURIComponent(salonId)}`;
}

export const useBookingStore = defineStore("booking", {
  state: () => ({
    salonId: null as string | null,
    salon: null as PublicSalon | null,
    offerings: [] as PublicOffering[],
    /** Créneaux déjà chargés (la limite publique est de 20 requêtes/min par IP). */
    slotsCache: {} as Record<string, PublicSlots>,
  }),
  actions: {
    async loadSalon(salonId: string): Promise<void> {
      if (this.salonId !== salonId) {
        this.salonId = null;
        this.salon = null;
        this.offerings = [];
      }
      this.slotsCache = {};
      const base = publicPath(salonId);
      const [salon, offerings] = await Promise.all([
        apiGet<PublicSalon>(base),
        apiGet<PublicOffering[]>(`${base}/services`),
      ]);
      this.salon = salon;
      this.offerings = offerings;
      this.salonId = salonId;
    },

    async fetchSlots(
      salonId: string,
      serviceId: string,
      resourceId: string | null,
      from: string,
      to: string,
    ): Promise<PublicSlots> {
      const key = [salonId, serviceId, resourceId ?? "*", from, to].join("|");
      const cached = this.slotsCache[key];
      if (cached) {
        return cached;
      }
      let query = `from=${from}&to=${to}`;
      if (resourceId) {
        query += `&resourceId=${encodeURIComponent(resourceId)}`;
      }
      const slots = await apiGet<PublicSlots>(
        `${publicPath(salonId)}/services/${encodeURIComponent(serviceId)}/slots?${query}`,
      );
      this.slotsCache[key] = slots;
      return slots;
    },

    clearSlots(): void {
      this.slotsCache = {};
    },

    async book(
      salonId: string,
      request: BookingRequest,
    ): Promise<OnlineBookingResult> {
      const result = await apiPost<OnlineBookingResult>(
        `/api/me/salons/${encodeURIComponent(salonId)}/bookings`,
        request,
        requireToken(),
      );
      this.slotsCache = {};
      return result;
    },
  },
});
