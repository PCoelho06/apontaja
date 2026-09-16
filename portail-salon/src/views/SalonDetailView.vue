<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";

import { ApiError } from "@/lib/apiClient";
import { useSalonStore } from "@/stores/salon";
import { useStaffStore } from "@/stores/staff";

const route = useRoute();
const salonId = computed(() => route.params.salonId as string);

const salonStore = useSalonStore();
const staffStore = useStaffStore();

const isLoading = ref(true);
const loadError = ref("");

const inviteEmail = ref("");
const inviteRole = ref("EMPLOYEE");
const inviteError = ref("");
const isInviting = ref(false);

const rowErrors = ref<Record<string, string>>({});

async function loadAll() {
  isLoading.value = true;
  loadError.value = "";
  try {
    await Promise.all([
      salonStore.fetchSalon(salonId.value),
      staffStore.fetchMembers(salonId.value),
      staffStore.fetchInvitations(salonId.value),
    ]);
  } catch (error) {
    loadError.value =
      error instanceof ApiError
        ? error.message
        : "Impossible de charger ce salon.";
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadAll);

function shortId(id: string) {
  return id.slice(0, 8);
}

async function handleInvite() {
  inviteError.value = "";
  isInviting.value = true;
  try {
    await staffStore.inviteMember(
      salonId.value,
      inviteEmail.value,
      inviteRole.value,
    );
    inviteEmail.value = "";
  } catch (error) {
    inviteError.value =
      error instanceof ApiError
        ? error.message
        : "Impossible d'envoyer l'invitation.";
  } finally {
    isInviting.value = false;
  }
}

async function handleChangeRole(staffMembershipId: string, newRole: string) {
  rowErrors.value = { ...rowErrors.value, [staffMembershipId]: "" };
  try {
    await staffStore.changeRole(salonId.value, staffMembershipId, newRole);
  } catch (error) {
    rowErrors.value = {
      ...rowErrors.value,
      [staffMembershipId]:
        error instanceof ApiError ? error.message : "Action impossible.",
    };
  }
}

async function handleRemove(staffMembershipId: string) {
  rowErrors.value = { ...rowErrors.value, [staffMembershipId]: "" };
  try {
    await staffStore.removeMember(salonId.value, staffMembershipId);
  } catch (error) {
    rowErrors.value = {
      ...rowErrors.value,
      [staffMembershipId]:
        error instanceof ApiError ? error.message : "Action impossible.",
    };
  }
}
</script>

<template>
  <main class="min-h-screen bg-paper px-6 py-12">
    <div class="mx-auto max-w-2xl">
      <RouterLink
        :to="{ name: 'home' }"
        class="text-sm text-wine hover:underline"
      >
        ← Retour à vos salons
      </RouterLink>

      <p
        v-if="isLoading"
        class="mt-4 text-sm text-ink/70"
      >
        Chargement...
      </p>
      <p
        v-else-if="loadError"
        class="mt-4 text-sm text-danger"
      >
        {{ loadError }}
      </p>

      <template v-else-if="salonStore.currentSalon">
        <h1 class="mt-4 font-display text-2xl text-ink">
          {{ salonStore.currentSalon.name }}
        </h1>
        <p class="text-sm text-ink/70">
          {{ salonStore.currentSalon.address }},
          {{ salonStore.currentSalon.postalCode }}
          {{ salonStore.currentSalon.city }},
          {{ salonStore.currentSalon.country }}
        </p>

        <!-- Équipe -->
        <section class="mt-8">
          <h2 class="text-lg font-medium text-ink">
            Équipe
          </h2>
          <p class="mt-1 text-xs text-ink/50">
            L'identité complète des membres n'est pas encore disponible ici —
            identifiant technique affiché en attendant.
          </p>

          <ul
            class="mt-3 divide-y divide-border rounded-md border border-border bg-white"
          >
            <li
              v-for="member in staffStore.members"
              :key="member.staffMembershipId"
              class="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <span class="block text-sm text-ink">Compte {{ shortId(member.accountId) }}…</span>
                <span class="block text-xs text-ink/50">Membre depuis
                  {{ new Date(member.since).toLocaleDateString() }}</span>
              </div>
              <div class="flex items-center gap-2">
                <select
                  :value="member.role"
                  class="rounded-md border border-border bg-white px-2 py-1 text-sm text-ink"
                  @change="
                    handleChangeRole(
                      member.staffMembershipId,
                      ($event.target as HTMLSelectElement).value,
                    )
                  "
                >
                  <option value="OWNER">
                    OWNER
                  </option>
                  <option value="MANAGER">
                    MANAGER
                  </option>
                  <option value="EMPLOYEE">
                    EMPLOYEE
                  </option>
                </select>
                <button
                  type="button"
                  class="rounded-md border border-danger px-2 py-1 text-xs font-medium text-danger hover:bg-danger hover:text-paper"
                  @click="handleRemove(member.staffMembershipId)"
                >
                  Retirer
                </button>
              </div>
              <p
                v-if="rowErrors[member.staffMembershipId]"
                class="w-full text-xs text-danger sm:text-right"
              >
                {{ rowErrors[member.staffMembershipId] }}
              </p>
            </li>
            <li
              v-if="staffStore.members.length === 0"
              class="px-4 py-3 text-sm text-ink/60"
            >
              Aucun membre.
            </li>
          </ul>
        </section>

        <!-- Invitations en attente -->
        <section class="mt-8">
          <h2 class="text-lg font-medium text-ink">
            Invitations en attente
          </h2>
          <ul
            class="mt-3 divide-y divide-border rounded-md border border-border bg-white"
          >
            <li
              v-for="invitation in staffStore.pendingInvitations"
              :key="invitation.invitationId"
              class="flex items-center justify-between px-4 py-3"
            >
              <span class="text-sm text-ink">{{ invitation.email }}</span>
              <span class="text-xs font-medium uppercase text-wine">{{
                invitation.role
              }}</span>
            </li>
            <li
              v-if="staffStore.pendingInvitations.length === 0"
              class="px-4 py-3 text-sm text-ink/60"
            >
              Aucune invitation en attente.
            </li>
          </ul>
        </section>

        <!-- Nouvelle invitation -->
        <section class="mt-8">
          <h2 class="text-lg font-medium text-ink">
            Inviter un membre
          </h2>
          <form
            class="mt-3 flex flex-col gap-3 sm:flex-row sm:items-end"
            @submit.prevent="handleInvite"
          >
            <div class="flex-1">
              <label
                for="inviteEmail"
                class="block text-sm font-medium text-ink"
              >Email</label>
              <input
                id="inviteEmail"
                v-model="inviteEmail"
                type="email"
                required
                class="mt-1.5 block w-full rounded-md border border-border bg-white px-3 py-2 text-sm text-ink focus:border-wine focus:outline-none focus:ring-1 focus:ring-wine"
              >
            </div>
            <div>
              <label
                for="inviteRole"
                class="block text-sm font-medium text-ink"
              >Rôle</label>
              <select
                id="inviteRole"
                v-model="inviteRole"
                class="mt-1.5 rounded-md border border-border bg-white px-3 py-2 text-sm text-ink"
              >
                <option value="OWNER">
                  OWNER
                </option>
                <option value="MANAGER">
                  MANAGER
                </option>
                <option value="EMPLOYEE">
                  EMPLOYEE
                </option>
              </select>
            </div>
            <button
              type="submit"
              :disabled="isInviting"
              class="rounded-md bg-wine px-4 py-2 text-sm font-medium text-paper hover:bg-wine/90 disabled:opacity-50"
            >
              {{ isInviting ? "Envoi..." : "Inviter" }}
            </button>
          </form>
          <p
            v-if="inviteError"
            class="mt-2 text-sm text-danger"
          >
            {{ inviteError }}
          </p>
        </section>
      </template>
    </div>
  </main>
</template>
