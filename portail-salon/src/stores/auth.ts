import { defineStore } from 'pinia'

import { apiPost, apiPostWithCsrf } from '@/lib/apiClient'

interface AuthAccount {
  accountId: string
  email: string
}

interface LoginResponse {
  accountId: string
  email: string
  accessToken: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // Volontairement en mémoire uniquement (jamais localStorage/sessionStorage) —
    // décision actée dans le contexte projet. Se perd à un rechargement de page ;
    // pas de restauration silencieuse via /refresh au démarrage pour l'instant,
    // hors périmètre de cette tranche (voir notes de livraison).
    accessToken: null as string | null,
    account: null as AuthAccount | null,
  }),

  getters: {
    isAuthenticated: (state) => state.accessToken !== null,
  },

  actions: {
    async login(email: string, password: string) {
      const result = await apiPost<LoginResponse>('/api/auth/login', { email, password })
      this.accessToken = result.accessToken
      this.account = { accountId: result.accountId, email: result.email }
    },

    async register(email: string, password: string) {
      // Pas de connexion automatique après l'inscription : le backend ne
      // délivre pas de tokens sur /register (uniquement sur /login).
      await apiPost<void>('/api/auth/register', { email, password })
    },

    async requestPasswordReset(email: string) {
      await apiPost<void>('/api/auth/forgot-password', { email })
    },

    async resetPassword(token: string, newPassword: string) {
      await apiPost<void>('/api/auth/reset-password', { token, newPassword })
    },

    async confirmEmail(token: string) {
      await apiPost<void>('/api/auth/confirm-email', { token })
    },

    async resendVerificationEmail(email: string) {
      await apiPost<void>('/api/auth/resend-verification-email', { email })
    },

    async logout() {
      try {
        await apiPostWithCsrf<void>('/api/auth/logout')
      } finally {
        this.accessToken = null
        this.account = null
      }
    },
  },
})
