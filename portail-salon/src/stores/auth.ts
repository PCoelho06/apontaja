import { defineStore } from "pinia";

import {
  apiGet,
  apiPost,
  apiPostWithCsrf,
  configureAuthClient,
} from "@/lib/apiClient";

interface AuthAccount {
  accountId: string;
  email: string;
  firstName: string;
  lastName: string;
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
  firstName: string;
  lastName: string;
  emailVerified: boolean;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
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
      try {
        await this.loadAccount();
      } catch (error) {
        this.accessToken = null;
        this.account = null;
        throw error;
      }
    },

    async register(
      email: string,
      password: string,
      firstName: string,
      lastName: string,
    ) {
      await apiPost<void>("/api/auth/register", {
        email,
        password,
        firstName,
        lastName,
      });
    },

    /** Lit l'identité du compte connecté (prénom, nom, email) avec le jeton courant. */
    async loadAccount() {
      const me = await apiGet<MeResponse>(
        "/api/account/me",
        this.accessToken ?? undefined,
      );

      this.account = {
        accountId: me.accountId,
        email: me.email,
        firstName: me.firstName,
        lastName: me.lastName,
      };
    },

    async restoreSession() {
      try {
        const refreshResult =
          await apiPostWithCsrf<RefreshResponse>("/api/auth/refresh");

        this.accessToken = refreshResult.accessToken;
        await this.loadAccount();
      } catch {
        this.accessToken = null;
        this.account = null;
      }
    },

    async requestPasswordReset(email: string) {
      await apiPost<void>("/api/auth/forgot-password", { email });
    },

    async resetPassword(token: string, newPassword: string) {
      await apiPost<void>("/api/auth/reset-password", {
        token,
        newPassword,
      });
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
  },
});

configureAuthClient({
  getAccessToken: () => useAuthStore().accessToken,

  setAccessToken: (accessToken) => {
    useAuthStore().accessToken = accessToken;
  },

  clearSession: () => {
    const store = useAuthStore();
    store.accessToken = null;
    store.account = null;
  },
});
