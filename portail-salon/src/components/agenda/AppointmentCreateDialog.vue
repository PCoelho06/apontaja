<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";

import {
  UiAlert,
  UiButton,
  UiDialog,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

import { describeIssues } from "@/lib/availabilityIssues";
import {
  formatCents,
  formatDuration,
} from "@/lib/catalogFormat";
import { errorMessage } from "@/lib/errorMessage";
import { instantToZonedLocal, zonedLocalToInstant } from "@/lib/zonedTime";
import { useAppointmentStore } from "@/stores/appointment";
import { useCustomerStore, type NewCustomer } from "@/stores/customer";
import { useSalonServiceStore } from "@/stores/salonService";

const props = defineProps<{
  salonId: string;
  timeZone: string;
  resources: Array<{ resourceId: string; name: string }>;
  initialResourceId: string | null;
  /** Valeur d'un input datetime-local (heure du salon). */
  initialStart: string;
}>();

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{ created: [] }>();

const appointmentStore = useAppointmentStore();
const customerStore = useCustomerStore();
const serviceStore = useSalonServiceStore();

const resourceId = ref("");
const start = ref("");
const serviceId = ref("");
const customerId = ref("");
const search = ref("");
const newCustomer = ref(false);
const customerForm = reactive({
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
});
const formError = ref<string | null>(null);
const issues = ref<string[]>([]);
const submitting = ref(false);

function reset() {
  resourceId.value = props.initialResourceId ?? props.resources[0]?.resourceId ?? "";
  start.value = props.initialStart;
  serviceId.value = "";
  customerId.value = "";
  search.value = "";
  newCustomer.value = false;
  customerForm.firstName = "";
  customerForm.lastName = "";
  customerForm.email = "";
  customerForm.phone = "";
  formError.value = null;
  issues.value = [];
}

async function loadLinks() {
  try {
    await Promise.all(
      serviceStore.services.map((service) =>
        serviceStore.fetchLinks(props.salonId, service.serviceId),
      ),
    );
  } catch (e) {
    formError.value = errorMessage(e, "Impossible de charger les prestations.");
  }
}

watch(
  open,
  (isOpen) => {
    if (isOpen) {
      reset();
      loadLinks();
    }
  },
  { immediate: true },
);

/** Prestations réservables sur la ressource choisie, avec durée et prix effectifs. */
const serviceChoices = computed(() =>
  serviceStore.services.flatMap((service) => {
    const link = serviceStore.links[service.serviceId]?.find(
      (l) => l.resourceId === resourceId.value,
    );
    return link
      ? [
          {
            serviceId: service.serviceId,
            label: `${service.name} · ${formatDuration(link.effectiveDurationMinutes)} · ${formatCents(link.effectivePriceCents)}`,
            durationMinutes: link.effectiveDurationMinutes,
          },
        ]
      : [];
  }),
);

const selectedChoice = computed(
  () => serviceChoices.value.find((c) => c.serviceId === serviceId.value) ?? null,
);

watch(resourceId, () => {
  if (!selectedChoice.value) {
    serviceId.value = "";
  }
});

const endHint = computed(() => {
  const choice = selectedChoice.value;
  if (!choice || !start.value) {
    return undefined;
  }
  try {
    const startAt = zonedLocalToInstant(start.value, props.timeZone);
    const end = new Date(new Date(startAt).getTime() + choice.durationMinutes * 60_000);
    return `Fin prévue à ${instantToZonedLocal(end.toISOString(), props.timeZone).slice(11, 16)}`;
  } catch {
    return undefined;
  }
});

const serviceHint = computed(() => {
  if (serviceChoices.value.length === 0) {
    return "Aucune prestation n'est associée à cette ressource (voir le catalogue).";
  }
  return endHint.value;
});

const filteredCustomers = computed(() => {
  const needle = search.value.trim().toLowerCase();
  return customerStore.customers
    .filter((c) =>
      needle === ""
        ? true
        : [c.firstName, c.lastName, c.email ?? "", c.phone ?? ""]
            .join(" ")
            .toLowerCase()
            .includes(needle),
    )
    .sort((a, b) => a.lastName.localeCompare(b.lastName))
    .slice(0, 50);
});

function customerLabel(c: { firstName: string; lastName: string; email: string | null; phone: string | null }) {
  const contact = c.email ?? c.phone;
  return contact ? `${c.firstName} ${c.lastName} · ${contact}` : `${c.firstName} ${c.lastName}`;
}

function buildCustomer(): NewCustomer | null {
  const firstName = customerForm.firstName.trim();
  const lastName = customerForm.lastName.trim();
  const email = customerForm.email.trim();
  const phone = customerForm.phone.trim();
  if (!firstName || !lastName) {
    formError.value = "Le prénom et le nom du client sont obligatoires.";
    return null;
  }
  if (!email && !phone) {
    formError.value = "Renseignez au moins un email ou un téléphone pour le client.";
    return null;
  }
  return { firstName, lastName, email: email || null, phone: phone || null, internalNotes: null };
}

async function submit() {
  formError.value = null;
  issues.value = [];
  if (!resourceId.value || !serviceId.value || !start.value) {
    formError.value = "Choisissez une ressource, une prestation et un début.";
    return;
  }
  const customerPayload = newCustomer.value ? buildCustomer() : null;
  if (newCustomer.value && !customerPayload) {
    return;
  }
  if (!newCustomer.value && !customerId.value) {
    formError.value = "Choisissez un client ou créez-en un.";
    return;
  }

  submitting.value = true;
  try {
    const startAt = zonedLocalToInstant(start.value, props.timeZone);
    let profileId = customerId.value;
    if (customerPayload) {
      const created = await customerStore.createCustomer(props.salonId, customerPayload);
      // Le client existe désormais : un nouvel essai ne doit pas le recréer (pas de dédoublonnage côté back).
      profileId = created.customerProfileId;
      customerId.value = profileId;
      newCustomer.value = false;
      search.value = "";
    }
    await appointmentStore.createAppointment(props.salonId, {
      customerProfileId: profileId,
      serviceId: serviceId.value,
      resourceId: resourceId.value,
      startAt,
    });
    emit("created");
    open.value = false;
  } catch (e) {
    issues.value = describeIssues(e);
    formError.value =
      issues.value.length > 0
        ? null
        : e instanceof Error
          ? e.message
          : "Réservation impossible.";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <UiDialog
    v-model:open="open"
    title="Nouveau rendez-vous"
  >
    <form
      class="space-y-4"
      @submit.prevent="submit"
    >
      <UiSelect
        v-model="resourceId"
        label="Ressource"
      >
        <option
          v-for="resource in resources"
          :key="resource.resourceId"
          :value="resource.resourceId"
        >
          {{ resource.name }}
        </option>
      </UiSelect>

      <UiInput
        v-model="start"
        type="datetime-local"
        label="Début"
      />

      <UiSelect
        v-model="serviceId"
        label="Prestation"
        :hint="serviceHint"
      >
        <option
          value=""
          disabled
        >
          Choisir une prestation…
        </option>
        <option
          v-for="choice in serviceChoices"
          :key="choice.serviceId"
          :value="choice.serviceId"
        >
          {{ choice.label }}
        </option>
      </UiSelect>

      <fieldset class="space-y-3 rounded-md border border-border p-3">
        <legend class="px-1 text-sm font-medium text-ink">
          Client
        </legend>

        <template v-if="!newCustomer">
          <UiInput
            v-model="search"
            label="Rechercher"
            autofocus
          />
          <UiSelect
            v-model="customerId"
            label="Client"
          >
            <option
              value=""
              disabled
            >
              Choisir un client…
            </option>
            <option
              v-for="customer in filteredCustomers"
              :key="customer.customerProfileId"
              :value="customer.customerProfileId"
            >
              {{ customerLabel(customer) }}
            </option>
          </UiSelect>
        </template>

        <template v-else>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <UiInput
              v-model="customerForm.firstName"
              label="Prénom"
              autofocus
            />
            <UiInput
              v-model="customerForm.lastName"
              label="Nom"
            />
            <UiInput
              v-model="customerForm.email"
              type="email"
              label="Email"
            />
            <UiInput
              v-model="customerForm.phone"
              type="tel"
              label="Téléphone"
            />
          </div>
          <p class="text-xs text-ink/60">
            Au moins un email ou un téléphone est requis.
          </p>
        </template>

        <UiButton
          variant="secondary"
          @click="newCustomer = !newCustomer"
        >
          {{ newCustomer ? "Choisir un client existant" : "Nouveau client" }}
        </UiButton>
      </fieldset>

      <UiAlert
        v-if="issues.length > 0"
        variant="error"
      >
        <p class="font-medium">
          Ce créneau n'est pas disponible :
        </p>
        <ul class="list-disc pl-5">
          <li
            v-for="issue in issues"
            :key="issue"
          >
            {{ issue }}
          </li>
        </ul>
      </UiAlert>
      <UiAlert
        v-else-if="formError"
        variant="error"
      >
        {{ formError }}
      </UiAlert>

      <div class="flex justify-end gap-2">
        <UiButton
          variant="secondary"
          @click="open = false"
        >
          Annuler
        </UiButton>
        <UiButton
          type="submit"
          :loading="submitting"
        >
          Réserver
        </UiButton>
      </div>
    </form>
  </UiDialog>
</template>
