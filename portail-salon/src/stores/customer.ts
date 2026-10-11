import { defineStore } from "pinia";

import { apiGet, apiPost } from "@/lib/apiClient";
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

/** Au moins un email ou un téléphone est exigé par le back (400 sinon). */
export interface NewCustomer {
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  internalNotes: string | null;
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
        /** Crée un client, recharge la liste et renvoie le profil créé. */
    async createCustomer(
      salonId: string,
      payload: NewCustomer,
    ): Promise<Customer> {
      const created = await apiPost<Partial<Customer> | undefined>(
        `/api/salons/${salonId}/customers`,
        payload,
        requireToken(),
      );
      await this.fetchCustomers(salonId);
      const byId = created?.customerProfileId
        ? this.customers.find(
            (c) => c.customerProfileId === created.customerProfileId,
          )
        : undefined;
      // Repli si la réponse ne contient pas l'identifiant : le plus récent du même nom.
      const found =
        byId ??
        this.customers
          .filter(
            (c) =>
              c.firstName === payload.firstName &&
              c.lastName === payload.lastName,
          )
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
      if (!found) {
        throw new Error("Client créé mais introuvable dans la liste.");
      }
      return found;
    },
  },
});
