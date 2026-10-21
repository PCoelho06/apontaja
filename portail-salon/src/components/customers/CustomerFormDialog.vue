<script setup lang="ts">
import { reactive, ref, watch } from "vue";

import {
  UiAlert,
  UiButton,
  UiDialog,
  UiInput,
  UiTextarea,
} from "@apontaja/ui-kit";

import { ApiError } from "@apontaja/web-core/api-client";
import { buildCustomerPayload } from "@/lib/customerForm";
import { errorMessage } from "@apontaja/web-core/error-message";
import { useCustomerStore, type Customer } from "@/stores/customer";

const props = defineProps<{
  salonId: string;
  /** Client à modifier ; `null` pour en créer un. */
  customer: Customer | null;
}>();

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{ saved: [customer: Customer] }>();

const customerStore = useCustomerStore();

const form = reactive({
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  internalNotes: "",
});
const formError = ref<string | null>(null);
const fieldErrors = ref<Record<string, string>>({});
const submitting = ref(false);

function reset() {
  form.firstName = props.customer?.firstName ?? "";
  form.lastName = props.customer?.lastName ?? "";
  form.email = props.customer?.email ?? "";
  form.phone = props.customer?.phone ?? "";
  form.internalNotes = props.customer?.internalNotes ?? "";
  formError.value = null;
  fieldErrors.value = {};
}

watch(
  open,
  (isOpen) => {
    if (isOpen) {
      reset();
    }
  },
  { immediate: true },
);

async function submit() {
  formError.value = null;
  fieldErrors.value = {};
  const result = buildCustomerPayload(form);
  if ("error" in result) {
    formError.value = result.error;
    return;
  }
  submitting.value = true;
  try {
    const saved = props.customer
      ? await customerStore.updateCustomer(
          props.salonId,
          props.customer.customerProfileId,
          result.payload,
        )
      : await customerStore.createCustomer(props.salonId, result.payload);
    emit("saved", saved);
    open.value = false;
  } catch (e) {
    formError.value = errorMessage(e, "Enregistrement impossible.");
    fieldErrors.value = e instanceof ApiError ? (e.fieldErrors ?? {}) : {};
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <UiDialog
    v-model:open="open"
    :title="customer ? 'Modifier le client' : 'Nouveau client'"
  >
    <form
      class="space-y-4"
      @submit.prevent="submit"
    >
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <UiInput
          v-model="form.firstName"
          label="Prénom"
          maxlength="100"
          autofocus
          :error="fieldErrors.firstName"
        />
        <UiInput
          v-model="form.lastName"
          label="Nom"
          maxlength="100"
          :error="fieldErrors.lastName"
        />
        <UiInput
          v-model="form.email"
          type="email"
          label="Email"
          :error="fieldErrors.email"
        />
        <UiInput
          v-model="form.phone"
          type="tel"
          label="Téléphone"
          :error="fieldErrors.phone"
        />
      </div>
      <p class="text-xs text-ink/60">
        Au moins un email ou un téléphone est requis.
      </p>
      <UiTextarea
        v-model="form.internalNotes"
        label="Notes internes (visibles du salon uniquement)"
        maxlength="2000"
        :error="fieldErrors.internalNotes"
      />

      <UiAlert
        v-if="formError"
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
          Enregistrer
        </UiButton>
      </div>
    </form>
  </UiDialog>
</template>
