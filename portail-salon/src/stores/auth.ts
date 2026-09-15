import { defineStore } from "pinia";

import { apiGet, apiPost, apiPostWithCsrf } from "@/lib/apiClient";

interface AuthAccount {
  accountId: string;
  email: string;
}

interface LoginResponse {
  accountId: string;
  email: string;
  accessToken: string;
}

interface RefreshResponse {
  accountId: string;
  accessToken: string;
}

interface MeResponse {
  accountId: string;
  email: string;
  emailVerified: boolean;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    // Volontairement en mémoire uniquement (jamais localStorage/sessionStorage) —
    // décision actée dans le contexte projet.
    accessToken: null as string | null,
    account: null as AuthAccount | null,
  }),

  getters: {
    isAuthenticated: (state) => state.accessToken !== null,
  },

  actions: {
    async login(email: string, password: string) {
      const result = await apiPost<LoginResponse>("/api/auth/login", {
        email,
        password,
      });
      this.accessToken = result.accessToken;
      this.account = { accountId: result.accountId, email: result.email };
    },

    async register(email: string, password: string) {
      // Pas de connexion automatique après l'inscription : le backend ne
      // délivre pas de tokens sur /register (uniquement sur /login).
      await apiPost<void>("/api/auth/register", { email, password });
    },

    async requestPasswordReset(email: string) {
      await apiPost<void>("/api/auth/forgot-password", { email });
    },

    async resetPassword(token: string, newPassword: string) {
      await apiPost<void>("/api/auth/reset-password", { token, newPassword });
    },

    async confirmEmail(token: string) {
      await apiPost<void>("/api/auth/confirm-email", { token });
    },

    async resendVerificationEmail(email: string) {
      await apiPost<void>("/api/auth/resend-verification-email", { email });
    },

    async logout() {
      try {
        await apiPostWithCsrf<void>("/api/auth/logout");
      } finally {
        this.accessToken = null;
        this.account = null;
      }
    },

    /** Appelée une seule fois au boot de l'app (voir main.ts), avant le montage du router.
     * S'appuie sur le cookie refresh_token httpOnly s'il existe et est valide — silencieux dans
     * tous les cas d'échec (pas de session préexistante, cookie expiré, etc.), c'est le
     * comportement normal d'un visiteur non connecté, pas une erreur à afficher. */
    async restoreSession() {
      try {
        const refreshResult =
          await apiPostWithCsrf<RefreshResponse>("/api/auth/refresh");
        this.accessToken = refreshResult.accessToken;

        const me = await apiGet<MeResponse>(
          "/api/account/me",
          this.accessToken,
        );
        this.account = { accountId: me.accountId, email: me.email };
      } catch {
        this.accessToken = null;
        this.account = null;
      }
    },
  },
});
