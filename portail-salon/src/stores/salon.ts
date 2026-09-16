import { defineStore } from "pinia";

import { apiGet, apiPost } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";

interface CreateSalonPayload {
  name: string;
  address: string;
  postalCode: string;
  city: string;
  country: string;
  timezone: string;
  phone?: string;
}

interface CreateSalonResponse {
  salonId: string;
  organizationId: string;
}

export interface SalonListItem {
  salonId: string;
  organizationId: string;
  name: string;
  address: string;
  postalCode: string;
  city: string;
  country: string;
  phone: string | null;
  timezone: string;
  role: "OWNER" | "MANAGER" | "EMPLOYEE" | "ORGANIZATION_OWNER";
}

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SalonDetail {
  salonId: string;
  organizationId: string;
  name: string;
  address: string;
  postalCode: string;
  city: string;
  country: string;
  phone: string | null;
  timezone: string;
}

export const useSalonStore = defineStore("salon", {
  state: () => ({
    lastCreatedSalonId: null as string | null,
    salons: [] as SalonListItem[],
    currentSalon: null as SalonDetail | null,
  }),

  actions: {
    async createSalon(
      payload: CreateSalonPayload,
    ): Promise<CreateSalonResponse> {
      const auth = useAuthStore();
      if (!auth.accessToken) {
        throw new Error("Non authentifié.");
      }
      const result = await apiPost<CreateSalonResponse>(
        "/api/salons",
        payload,
        auth.accessToken,
      );
      this.lastCreatedSalonId = result.salonId;
      return result;
    },

    /** Pas de pagination gérée côté UI pour l'instant (page 0 par défaut, taille backend = 20) —
     * suffisant tant qu'un compte n'a pas plus de 20 salons accessibles. */
    async fetchSalons() {
      const auth = useAuthStore();
      if (!auth.accessToken) {
        throw new Error("Non authentifié.");
      }
      const result = await apiGet<PageResponse<SalonListItem>>(
        "/api/salons",
        auth.accessToken,
      );
      this.salons = result.content;
    },

    async fetchSalon(salonId: string) {
      const auth = useAuthStore();
      if (!auth.accessToken) {
        throw new Error("Non authentifié.");
      }
      this.currentSalon = await apiGet<SalonDetail>(
        `/api/salons/${salonId}`,
        auth.accessToken,
      );
    },
  },
});
