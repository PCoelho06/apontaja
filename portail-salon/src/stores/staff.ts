import { defineStore } from "pinia";

import { apiDelete, apiGet, apiPatch, apiPost } from "@/lib/apiClient";
import { useAuthStore } from "@/stores/auth";

export interface StaffMember {
  staffMembershipId: string;
  accountId: string;
  role: string;
  since: string;
}

export interface StaffInvitation {
  invitationId: string;
  email: string;
  role: string;
  createdAt: string;
  expiresAt: string;
}

function requireToken(): string {
  const auth = useAuthStore();
  if (!auth.accessToken) {
    throw new Error("Non authentifié.");
  }
  return auth.accessToken;
}

export const useStaffStore = defineStore("staff", {
  state: () => ({
    members: [] as StaffMember[],
    pendingInvitations: [] as StaffInvitation[],
  }),

  actions: {
    async fetchMembers(salonId: string) {
      this.members = await apiGet<StaffMember[]>(
        `/api/salons/${salonId}/staff`,
        requireToken(),
      );
    },

    async fetchInvitations(salonId: string) {
      this.pendingInvitations = await apiGet<StaffInvitation[]>(
        `/api/salons/${salonId}/staff/invitations`,
        requireToken(),
      );
    },

    async inviteMember(salonId: string, email: string, role: string) {
      await apiPost<{ invitationId: string }>(
        `/api/salons/${salonId}/staff/invitations`,
        { email, role },
        requireToken(),
      );
      await this.fetchInvitations(salonId);
    },

    async changeRole(salonId: string, staffMembershipId: string, role: string) {
      await apiPatch<void>(
        `/api/salons/${salonId}/staff/${staffMembershipId}`,
        { role },
        requireToken(),
      );
      await this.fetchMembers(salonId);
    },

    async removeMember(salonId: string, staffMembershipId: string) {
      await apiDelete<void>(
        `/api/salons/${salonId}/staff/${staffMembershipId}`,
        requireToken(),
      );
      await this.fetchMembers(salonId);
    },
  },
});
