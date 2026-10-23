import { defineStore } from "pinia";

import { apiGet, apiPut } from "@apontaja/web-core/api-client";
import { requireToken } from "@apontaja/web-core/require-token";
import type { BookingSettings, Readiness } from "@/lib/bookingSettings";
import { useSalonServiceStore } from "@/stores/salonService";
import { useScheduleStore } from "@/stores/schedule";

function settingsUrl(salonId: string): string {
  return `/api/salons/${salonId}/booking-settings`;
}

/** Sans état : la vue garde sa copie éditable. PUT = remplacement complet. */
export const useBookingSettingsStore = defineStore("bookingSettings", {
  actions: {
    fetchSettings(salonId: string): Promise<BookingSettings> {
      return apiGet<BookingSettings>(settingsUrl(salonId), requireToken());
    },

    saveSettings(
      salonId: string,
      settings: BookingSettings,
    ): Promise<BookingSettings> {
      return apiPut<BookingSettings>(
        settingsUrl(salonId),
        settings,
        requireToken(),
      );
    },

    /**
     * Horaires du salon, prestations, et au moins une prestation associée à une ressource
     * (1 requête par prestation : acceptable pour quelques dizaines).
     */
    async fetchReadiness(salonId: string): Promise<Readiness> {
      const schedules = useScheduleStore();
      const services = useSalonServiceStore();
      const [slots] = await Promise.all([
        schedules.fetchSchedule(salonId, null),
        services.fetchServices(salonId),
      ]);
      const items = services.services;
      await Promise.all(
        items.map((service) => services.fetchLinks(salonId, service.serviceId)),
      );
      return {
        hasSalonHours: slots.length > 0,
        serviceCount: items.length,
        hasBookableService: items.some(
          (service) => (services.links[service.serviceId]?.length ?? 0) > 0,
        ),
      };
    },
  },
});
