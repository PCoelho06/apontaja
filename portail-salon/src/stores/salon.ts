import { defineStore } from "pinia";

import { apiPost } from "@/lib/apiClient";
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

export const useSalonStore = defineStore("salon", {
  state: () => ({
    lastCreatedSalonId: null as string | null,
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
  },
});
