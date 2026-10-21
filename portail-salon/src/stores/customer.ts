import { defineStore } from "pinia";

import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from "@apontaja/web-core/api-client";
import { requireToken } from "@apontaja/web-core/require-token";

import type { DuplicateReport } from "@/lib/customerDuplicates";

export interface Customer {
  customerProfileId: string;
  salonCustomerLinkId: string;
  salonId: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  internalNotes: string | null;
  marketingConsent: boolean;
  marketingConsentAt: string | null;
  marketingConsentRevokedAt: string | null;
  anonymized: boolean;
  createdAt: string;
  lastVisitAt: string | null;
  nextVisitAt: string | null;
}

/** Au moins un email ou un téléphone est exigé par le back (400 sinon). */
export interface NewCustomer {
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  internalNotes: string | null;
}

export interface CustomerAppointment {
  appointmentId: string;
  startAt: string;
  endAt: string;
  status: string;
  serviceId: string;
  priceAtBookingCents: number;
  durationAtBookingMinutes: number;
}

export interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CustomerSearch {
  q?: string;
  page?: number;
  size?: number;
}

// Ne garde que le résultat de la dernière recherche (saisie rapide).
let latestSearch = 0;

export const useCustomerStore = defineStore("customer", {
  state: () => ({
    /** Dernière page de résultats de recherche. */
    results: [] as Customer[],
    totalElements: 0,
    totalPages: 0,
    /**
     * Clients déjà vus, par identifiant : l'agenda y résout les noms des rendez-vous. Vidé quand on
     * change de salon.
     */
    byId: {} as Record<string, Customer>,
    cacheSalonId: null as string | null,
  }),

  actions: {
    remember(salonId: string, customers: Customer[]) {
      if (this.cacheSalonId !== salonId) {
        this.byId = {};
        this.cacheSalonId = salonId;
      }
      for (const customer of customers) {
        this.byId[customer.customerProfileId] = customer;
      }
    },

    async searchCustomers(
      salonId: string,
      { q = "", page = 0, size = 20 }: CustomerSearch = {},
    ): Promise<Paged<Customer>> {
      const request = ++latestSearch;
      const params = new URLSearchParams({
        page: String(page),
        size: String(size),
      });
      if (q.trim()) {
        params.set("q", q.trim());
      }
      const result = await apiGet<Paged<Customer>>(
        `/api/salons/${salonId}/customers?${params.toString()}`,
        requireToken(),
      );
      if (request === latestSearch) {
        this.remember(salonId, result.content);
        this.results = result.content;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
      }
      return result;
    },

    async fetchCustomer(
      salonId: string,
      customerId: string,
    ): Promise<Customer> {
      const customer = await apiGet<Customer>(
        `/api/salons/${salonId}/customers/${customerId}`,
        requireToken(),
      );
      this.remember(salonId, [customer]);
      return customer;
    },

    /**
     * Charge les clients de `ids` qui ne sont pas encore connus (une requête chacun). Un échec
     * isolé est ignoré : le client reste « inconnu » dans l'agenda.
     */
    async ensureCustomers(salonId: string, ids: string[]) {
      if (this.cacheSalonId !== salonId) {
        this.byId = {};
        this.cacheSalonId = salonId;
      }
      const missing = [...new Set(ids)].filter((id) => !this.byId[id]);
      await Promise.allSettled(
        missing.map((id) => this.fetchCustomer(salonId, id)),
      );
    },

    /** Crée un client (le back répond 201 avec la fiche créée). Les écrans relancent leur recherche. */
    async createCustomer(
      salonId: string,
      payload: NewCustomer,
    ): Promise<Customer> {
      const created = await apiPost<Customer>(
        `/api/salons/${salonId}/customers`,
        payload,
        requireToken(),
      );
      this.remember(salonId, [created]);
      return created;
    },

    async updateCustomer(
      salonId: string,
      customerId: string,
      payload: NewCustomer,
    ): Promise<Customer> {
      const updated = await apiPut<Customer>(
        `/api/salons/${salonId}/customers/${customerId}`,
        payload,
        requireToken(),
      );
      this.remember(salonId, [updated]);
      return updated;
    },

    async setMarketingConsent(
      salonId: string,
      customerId: string,
      consented: boolean,
    ): Promise<Customer> {
      const updated = await apiPatch<Customer>(
        `/api/salons/${salonId}/customers/${customerId}/marketing-consent`,
        { consented },
        requireToken(),
      );
      this.remember(salonId, [updated]);
      return updated;
    },

    /** Anonymisation RGPD (irréversible) ; recharge la fiche pour refléter l'état anonymisé. */
    async anonymizeCustomer(salonId: string, customerId: string) {
      await apiDelete(
        `/api/salons/${salonId}/customers/${customerId}`,
        requireToken(),
      );
      await this.fetchCustomer(salonId, customerId);
    },

    fetchHistory(
      salonId: string,
      customerId: string,
      page = 0,
      size = 10,
    ): Promise<Paged<CustomerAppointment>> {
      return apiGet<Paged<CustomerAppointment>>(
        `/api/salons/${salonId}/customers/${customerId}/appointments?page=${page}&size=${size}`,
        requireToken(),
      );
    },

    fetchDuplicates(salonId: string): Promise<DuplicateReport> {
      return apiGet<DuplicateReport>(
        `/api/salons/${salonId}/customers/duplicates`,
        requireToken(),
      );
    },

    /** Fusion irréversible : les profils absorbés disparaissent, le cache des clients est donc vidé. */
    async mergeCustomers(
      salonId: string,
      survivorId: string,
      absorbedIds: string[],
    ): Promise<Customer> {
      const survivor = await apiPost<Customer>(
        `/api/salons/${salonId}/customers/merge`,
        { survivorId, absorbedIds },
        requireToken(),
      );
      this.byId = {};
      this.cacheSalonId = null;
      this.remember(salonId, [survivor]);
      return survivor;
    },
  },
});
