<script setup lang="ts">
import { computed, ref, watch } from "vue";

import { UiAlert, UiButton, UiDialog } from "@apontaja/ui-kit";

import type { AppointmentStatus } from "@/lib/appointmentStatus";
import { STATUS_LABELS } from "@/lib/appointmentStatus";
import { formatCents } from "@/lib/catalogFormat";
import {
  describeAnonymizeError,
  describeConsentError,
  describeCustomerLoadError,
} from "@/lib/customerErrors";
import {
  NO_CONTACT_LABEL,
  contactLine,
  fullName,
  visitLabel,
} from "@/lib/customerFormat";
import {
  useCustomerStore,
  type Customer,
  type CustomerAppointment,
} from "@/stores/customer";
import { useSalonServiceStore } from "@/stores/salonService";

const HISTORY_PAGE_SIZE = 10;

const props = defineProps<{
  salonId: string;
  timeZone: string;
  customerId: string | null;
}>();

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{ edit: [customer: Customer]; changed: [] }>();

const customerStore = useCustomerStore();
const serviceStore = useSalonServiceStore();

const busy = ref(false);
const error = ref<string | null>(null);
const history = ref<CustomerAppointment[]>([]);
const historyPage = ref(0);
const historyTotalPages = ref(0);
const historyError = ref<string | null>(null);

const customer = computed(
  () => customerStore.byId[props.customerId ?? ""] ?? null,
);
const contact = computed(() => (customer.value ? contactLine(customer.value) : ""));

function serviceName(serviceId: string): string {
  return (
    serviceStore.services.find((s) => s.serviceId === serviceId)?.name ??
    "Serviço eliminado"
  );
}

function statusLabel(status: string): string {
  return STATUS_LABELS[status as AppointmentStatus] ?? status;
}

async function loadHistory(page: number) {
  if (!props.customerId) {
    return;
  }
  historyError.value = null;
  try {
    const result = await customerStore.fetchHistory(
      props.salonId,
      props.customerId,
      page,
      HISTORY_PAGE_SIZE,
    );
    history.value = result.content;
    historyPage.value = result.page;
    historyTotalPages.value = result.totalPages;
  } catch (e) {
    historyError.value = describeCustomerLoadError(e, "history");
  }
}

watch(
  () => [open.value, props.customerId],
  () => {
    busy.value = false;
    error.value = null;
    history.value = [];
    historyPage.value = 0;
    historyTotalPages.value = 0;
    if (open.value && props.customerId) {
      loadHistory(0);
    }
  },
  { immediate: true },
);

async function run(
  action: () => Promise<unknown>,
  describe: (e: unknown) => string,
) {
  busy.value = true;
  error.value = null;
  try {
    await action();
    emit("changed");
  } catch (e) {
    error.value = describe(e);
  } finally {
    busy.value = false;
  }
}

function setConsent(consented: boolean) {
  const id = props.customerId;
  if (id) {
    run(
      () => customerStore.setMarketingConsent(props.salonId, id, consented),
      describeConsentError,
    );
  }
}

function anonymize() {
  const id = props.customerId;
  const current = customer.value;
  if (!id || !current) {
    return;
  }
  const confirmed = globalThis.confirm(
    `Anonimizar ${fullName(current)}? O nome, o email, o telefone, ` +
      "as notas e o consentimento serão apagados definitivamente. " +
      "As marcações são conservadas.",
  );
  if (confirmed) {
    run(async () => {
      await customerStore.anonymizeCustomer(props.salonId, id);
      open.value = false;
    }, describeAnonymizeError);
  }
}
</script>

<template>
  <UiDialog
    v-model:open="open"
    title="Ficha do cliente"
    close-label="Fechar"
  >
    <div
      v-if="customer"
      class="space-y-5 text-sm text-ink"
    >
      <div>
        <p class="text-lg font-medium">
          {{ fullName(customer) }}
        </p>
        <p
          v-if="customer.anonymized"
          class="text-ink/60"
        >
          Cliente anonimizado: os dados pessoais foram apagados.
        </p>
        <p
          v-else
          class="text-ink/60"
        >
          {{ contact || NO_CONTACT_LABEL }}
        </p>
      </div>

      <dl class="grid grid-cols-[9rem_1fr] gap-x-3 gap-y-1.5">
        <dt class="text-ink/60">
          Última visita
        </dt>
        <dd>{{ visitLabel(customer.lastVisitAt, timeZone) }}</dd>
        <dt class="text-ink/60">
          Próxima marcação
        </dt>
        <dd>{{ visitLabel(customer.nextVisitAt, timeZone) }}</dd>
        <template v-if="!customer.anonymized">
          <dt class="text-ink/60">
            Notas internas
          </dt>
          <dd class="whitespace-pre-line">
            {{ customer.internalNotes || "—" }}
          </dd>
        </template>
      </dl>

      <UiAlert
        v-if="error"
        variant="error"
      >
        {{ error }}
      </UiAlert>

      <section
        v-if="!customer.anonymized"
        class="space-y-2 rounded-md border border-border p-3"
      >
        <p class="font-medium">
          Comunicações de marketing
        </p>
        <p class="text-ink/70">
          <template v-if="customer.marketingConsent">
            O cliente aceitou receber as comunicações deste salão
            (em {{ visitLabel(customer.marketingConsentAt, timeZone) }}).
          </template>
          <template v-else>
            Nenhum consentimento registado. Só registe um se o cliente o
            tiver dado expressamente.
          </template>
        </p>
        <UiButton
          v-if="customer.marketingConsent"
          variant="secondary"
          size="sm"
          :loading="busy"
          @click="setConsent(false)"
        >
          Retirar consentimento
        </UiButton>
        <UiButton
          v-else
          variant="secondary"
          size="sm"
          :loading="busy"
          @click="setConsent(true)"
        >
          Registar consentimento
        </UiButton>
      </section>

      <section class="space-y-2">
        <p class="font-medium">
          Histórico de marcações
        </p>
        <UiAlert
          v-if="historyError"
          variant="error"
        >
          {{ historyError }}
        </UiAlert>
        <ul
          v-else-if="history.length > 0"
          class="divide-y divide-border rounded-md border border-border"
        >
          <li
            v-for="item in history"
            :key="item.appointmentId"
            class="flex items-center justify-between gap-3 px-3 py-2"
          >
            <div>
              <p>{{ visitLabel(item.startAt, timeZone) }}</p>
              <p class="text-ink/60">
                {{ serviceName(item.serviceId) }} ·
                {{ formatCents(item.priceAtBookingCents) }}
              </p>
            </div>
            <span class="text-xs text-ink/70">{{ statusLabel(item.status) }}</span>
          </li>
        </ul>
        <p
          v-else
          class="text-ink/60"
        >
          Nenhuma marcação.
        </p>
        <div
          v-if="historyTotalPages > 1"
          class="flex items-center justify-between"
        >
          <UiButton
            variant="secondary"
            size="sm"
            :disabled="historyPage === 0"
            @click="loadHistory(historyPage - 1)"
          >
            ← Mais recentes
          </UiButton>
          <span class="text-xs text-ink/60">
            Página {{ historyPage + 1 }} / {{ historyTotalPages }}
          </span>
          <UiButton
            variant="secondary"
            size="sm"
            :disabled="historyPage + 1 >= historyTotalPages"
            @click="loadHistory(historyPage + 1)"
          >
            Mais antigas →
          </UiButton>
        </div>
      </section>

      <div class="flex flex-wrap justify-end gap-2">
        <UiButton
          v-if="!customer.anonymized"
          variant="danger"
          :disabled="busy"
          @click="anonymize"
        >
          Anonimizar
        </UiButton>
        <UiButton
          v-if="!customer.anonymized"
          :disabled="busy"
          @click="emit('edit', customer)"
        >
          Editar
        </UiButton>
      </div>
    </div>
  </UiDialog>
</template>



