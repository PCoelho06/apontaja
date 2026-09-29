import { computed } from "vue";

import { useSalonStore } from "@/stores/salon";

/**
 * Droits d'écriture sur le catalogue, alignés sur catalogManagementGuard :
 * OWNER, MANAGER ou OWNER d'organisation. Purement cosmétique : le back reste
 * l'autorité (403). Le rôle vient de la liste des salons (première page) ; s'il
 * est introuvable, l'UI reste en lecture seule.
 */
export function useCatalogAccess(salonId: () => string) {
  const salonStore = useSalonStore();

  const role = computed(
    () => salonStore.salons.find((s) => s.salonId === salonId())?.role ?? null,
  );

  const canManage = computed(
    () =>
      role.value === "OWNER" ||
      role.value === "MANAGER" ||
      role.value === "ORGANIZATION_OWNER",
  );

  return { role, canManage };
}
