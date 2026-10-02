import { defineStore } from "pinia";

import { apiGet } from "@/lib/apiClient";
import { requireToken } from "@/lib/requireToken";

export interface Customer {
  customerProfileId: string;
  salonCustomerLinkId: string;
  salonId: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  internalNotes: string | null;
  createdAt: string;
}

export const useCustomerStore = defineStore("customer", {
  state: () => ({
    customers: [] as Customer[],
    loadedSalonId: null as string | null,
  }),

  actions: {
    async fetchCustomers(salonId: string) {
      if (this.loadedSalonId !== salonId) {
        this.customers = [];
      }
      this.customers = await apiGet<Customer[]>(
        `/api/salons/${salonId}/customers`,
        requireToken(),
      );
      this.loadedSalonId = salonId;
    },
  },
});
