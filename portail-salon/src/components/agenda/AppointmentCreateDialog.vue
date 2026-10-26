<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";

import {
  UiAlert,
  UiButton,
  UiDialog,
  UiInput,
  UiSelect,
} from "@apontaja/ui-kit";

import {
  describeCreateError,
  describeCustomerSearchError,
  describeLocalTimeError,
  describeNewCustomerError,
} from "@/lib/appointmentErrors";
import { formatCents, formatDuration } from "@/lib/catalogFormat";
import { describeCatalogLoadError } from "@/lib/catalogErrors";
import { debounce } from "@/lib/debounce";
import { instantToZonedLocal, zonedLocalToInstant } from "@/lib/zonedTime";
import { useAppointmentStore } from "@/stores/appointment";
import {
  useCustomerStore,
  type Customer,
  type NewCustomer,
} from "@/stores/customer";
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
const selectedCustomer = ref<Customer | null>(null);
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
  resourceId.value =
    props.initialResourceId ?? props.resources[0]?.resourceId ?? "";
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
    formError.value = describeCatalogLoadError(e, "services");
  }
}

watch(
  open,
  (isOpen) => {
    if (isOpen) {
      reset();
      loadLinks();
      runSearch();
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
            label: [
              service.name,
              formatDuration(link.effectiveDurationMinutes),
              formatCents(link.effectivePriceCents),
            ].join(" · "),
            durationMinutes: link.effectiveDurationMinutes,
          },
        ]
      : [];
  }),
);

const selectedChoice = computed(
  () =>
    serviceChoices.value.find((c) => c.serviceId === serviceId.value) ?? null,
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
    const end = new Date(
      new Date(startAt).getTime() + choice.durationMinutes * 60_000,
    );
    return `Fim previsto às ${instantToZonedLocal(end.toISOString(), props.timeZone).slice(11, 16)}`;
  } catch {
    return undefined;
  }
});

const serviceHint = computed(() => {
  if (serviceChoices.value.length === 0) {
    return "Nenhum serviço está associado a este recurso. Associe-o no catálogo.";
  }
  return endHint.value;
});

async function runSearch() {
  try {
    await customerStore.searchCustomers(props.salonId, {
      q: search.value,
      size: 20,
    });
  } catch (e) {
    formError.value = describeCustomerSearchError(e);
  }
}

const debouncedSearch = debounce(runSearch, 300);
watch(search, () => debouncedSearch());
onBeforeUnmount(() => debouncedSearch.cancel());

watch(customerId, (id) => {
  selectedCustomer.value = customerStore.byId[id] ?? null;
});

/** Résultats de la recherche ; le client choisi reste proposé même s'il n'y figure plus. */
const customerChoices = computed(() => {
  const found = customerStore.results;
  const chosen = selectedCustomer.value;
  return chosen &&
    !found.some((c) => c.customerProfileId === chosen.customerProfileId)
    ? [chosen, ...found]
    : found;
});

function customerLabel(c: {
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
}) {
  const contact = c.email ?? c.phone;
  return contact
    ? `${c.firstName} ${c.lastName} · ${contact}`
    : `${c.firstName} ${c.lastName}`;
}

function buildCustomer(): NewCustomer | null {
  const firstName = customerForm.firstName.trim();
  const lastName = customerForm.lastName.trim();
  const email = customerForm.email.trim();
  const phone = customerForm.phone.trim();
  if (!firstName || !lastName) {
    formError.value = "O nome próprio e o apelido do cliente são obrigatórios.";
    return null;
  }
  if (!email && !phone) {
    formError.value =
      "Indique pelo menos um email ou um telefone para o cliente.";
    return null;
  }
  return {
    firstName,
    lastName,
    email: email || null,
    phone: phone || null,
    internalNotes: null,
  };
}

async function submit() {
  formError.value = null;
  issues.value = [];
  if (!resourceId.value || !serviceId.value || !start.value) {
    formError.value = "Escolha um recurso, um serviço e uma hora de início.";
    return;
  }
  const customerPayload = newCustomer.value ? buildCustomer() : null;
  if (newCustomer.value && !customerPayload) {
    return;
  }
  if (!newCustomer.value && !customerId.value) {
    formError.value = "Escolha um cliente ou crie um novo.";
    return;
  }

  let startAt: string;
  try {
    startAt = zonedLocalToInstant(start.value, props.timeZone);
  } catch (e) {
    formError.value = describeLocalTimeError(e);
    return;
  }

  submitting.value = true;
  try {
    let profileId = customerId.value;
    if (customerPayload) {
      let created: Customer;
      try {
        created = await customerStore.createCustomer(
          props.salonId,
          customerPayload,
        );
      } catch (e) {
        formError.value = describeNewCustomerError(e);
        return;
      }
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
    const failure = describeCreateError(e);
    issues.value = failure.issues;
    formError.value = failure.issues.length > 0 ? null : failure.message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <UiDialog
    v-model:open="open"
    title="Nova marcação"
    close-label="Fechar"
  >
    <form
      class="space-y-4"
      @submit.prevent="submit"
    >
      <UiSelect
        v-model="resourceId"
        label="Recurso"
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
        label="Início"
      />

      <UiSelect
        v-model="serviceId"
        label="Serviço"
        :hint="serviceHint"
      >
        <option
          value=""
          disabled
        >
          Escolher um serviço…
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
          Cliente
        </legend>

        <template v-if="!newCustomer">
          <UiInput
            v-model="search"
            label="Pesquisar"
            autofocus
          />
          <UiSelect
            v-model="customerId"
            label="Cliente"
          >
            <option
              value=""
              disabled
            >
              Escolher um cliente…
            </option>
            <option
              v-for="customer in customerChoices"
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
              label="Nome próprio"
              autofocus
            />
            <UiInput
              v-model="customerForm.lastName"
              label="Apelido"
            />
            <UiInput
              v-model="customerForm.email"
              type="email"
              label="Email"
            />
            <UiInput
              v-model="customerForm.phone"
              type="tel"
              label="Telefone"
            />
          </div>
          <p class="text-xs text-ink/60">
            É obrigatório indicar pelo menos um email ou um telefone.
          </p>
        </template>

        <UiButton
          variant="secondary"
          @click="newCustomer = !newCustomer"
        >
          {{ newCustomer ? "Escolher cliente existente" : "Novo cliente" }}
        </UiButton>
      </fieldset>

      <UiAlert
        v-if="issues.length > 0"
        variant="error"
      >
        <p class="font-medium">
          Este horário não está disponível:
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
          Cancelar
        </UiButton>
        <UiButton
          type="submit"
          :loading="submitting"
        >
          Marcar
        </UiButton>
      </div>
    </form>
  </UiDialog>
</template>



