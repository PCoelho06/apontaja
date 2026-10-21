import { defineStore } from "pinia";

/**
 * Email en attente de vérification après une inscription. Gardé en mémoire seulement : jamais
 * dans l'URL (historique, journaux) ni dans un stockage persistant.
 */
export const useVerificationStore = defineStore("verification", {
  state: () => ({
    pendingEmail: null as string | null,
  }),

  actions: {
    remember(email: string) {
      this.pendingEmail = email;
    },

    clear() {
      this.pendingEmail = null;
    },
  },
});
