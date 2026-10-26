<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";

import { UiAlert, UiBadge, UiButton, UiInput, UiSelect } from "@apontaja/ui-kit";

import {
  describeInviteError,
  describeMemberActionError,
  describeSalonLoadError,
} from "@/lib/salonErrors";
import { STAFF_ROLES, formatDate, roleLabel } from "@/lib/staffRoles";
import { useBookingRequestsStore } from "@/stores/bookingRequests";
import { useSalonStore } from "@/stores/salon";
import { useStaffStore } from "@/stores/staff";

const route = useRoute();
const salonId = computed(() => route.params.salonId as string);

const salonStore = useSalonStore();
const staffStore = useStaffStore();
const requestsStore = useBookingRequestsStore();

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
    loadError.value = describeSalonLoadError(error);
  } finally {
    isLoading.value = false;
  }
}

/** Compteur de la navigation : un échec (ex. droits) ne gêne pas la page, le badge reste simplement absent. */
async function loadRequestCount() {
  try {
    await requestsStore.fetchRequests(salonId.value);
  } catch {
    requestsStore.requests = [];
  }
}

onMounted(async () => {
  await loadAll();
  await loadRequestCount();
});

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
    inviteError.value = describeInviteError(error);
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
      [staffMembershipId]: describeMemberActionError(error),
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
      [staffMembershipId]: describeMemberActionError(error),
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
        ← Voltar aos seus salões
      </RouterLink>

      <p
        v-if="isLoading"
        class="mt-4 text-sm text-ink/70"
      >
        A carregar...
      </p>
      <UiAlert
        v-else-if="loadError"
        variant="error"
        class="mt-4"
      >
        {{ loadError }}
      </UiAlert>

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

        <RouterLink :to="{ name: 'catalog-resources', params: { salonId } }">
          Catálogo
        </RouterLink>
        <RouterLink
          :to="{ name: 'customers', params: { salonId } }"
          class="ml-4"
        >
          Clientes
        </RouterLink>
        <RouterLink
          :to="{ name: 'booking-requests', params: { salonId } }"
          class="ml-4"
        >
          Pedidos
          <UiBadge
            v-if="requestsStore.count > 0"
            tone="brass"
          >
            {{ requestsStore.count }}
          </UiBadge>
        </RouterLink>

        <!-- Equipa -->
        <section class="mt-8">
          <h2 class="text-lg font-medium text-ink">
            Equipa
          </h2>

          <ul
            class="mt-3 divide-y divide-border rounded-md border border-border bg-white"
          >
            <li
              v-for="member in staffStore.members"
              :key="member.staffMembershipId"
              class="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <span class="block text-sm text-ink">{{
                  member.displayName
                }}</span>
                <span
                  v-if="member.email"
                  class="block text-xs text-ink/50"
                >{{
                  member.email
                }}</span>
                <span class="block text-xs text-ink/50">Membro desde
                  {{ formatDate(member.since) }}</span>
              </div>
              <div class="flex items-end gap-2">
                <UiSelect
                  label="Função"
                  :model-value="member.role"
                  @update:model-value="
                    handleChangeRole(member.staffMembershipId, String($event))
                  "
                >
                  <option
                    v-for="role in STAFF_ROLES"
                    :key="role"
                    :value="role"
                  >
                    {{ roleLabel(role) }}
                  </option>
                </UiSelect>
                <UiButton
                  variant="danger"
                  size="sm"
                  @click="handleRemove(member.staffMembershipId)"
                >
                  Retirar
                </UiButton>
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
              Nenhum membro.
            </li>
          </ul>
        </section>

        <!-- Convites pendentes -->
        <section class="mt-8">
          <h2 class="text-lg font-medium text-ink">
            Convites pendentes
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
                roleLabel(invitation.role)
              }}</span>
            </li>
            <li
              v-if="staffStore.pendingInvitations.length === 0"
              class="px-4 py-3 text-sm text-ink/60"
            >
              Nenhum convite pendente.
            </li>
          </ul>
        </section>

        <!-- Novo convite -->
        <section class="mt-8">
          <h2 class="text-lg font-medium text-ink">
            Convidar um membro
          </h2>
          <form
            class="mt-3 flex flex-col gap-3 sm:flex-row sm:items-end"
            @submit.prevent="handleInvite"
          >
            <div class="flex-1">
              <UiInput
                v-model="inviteEmail"
                type="email"
                label="Email"
                autocomplete="off"
                required
              />
            </div>
            <UiSelect
              v-model="inviteRole"
              label="Função"
            >
              <option
                v-for="role in STAFF_ROLES"
                :key="role"
                :value="role"
              >
                {{ roleLabel(role) }}
              </option>
            </UiSelect>
            <UiButton
              type="submit"
              :loading="isInviting"
            >
              {{ isInviting ? "A enviar..." : "Convidar" }}
            </UiButton>
          </form>
          <UiAlert
            v-if="inviteError"
            variant="error"
            class="mt-2"
          >
            {{ inviteError }}
          </UiAlert>
        </section>
      </template>
    </div>
  </main>
</template>
